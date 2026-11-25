package com.booking.core.service;

import com.booking.common.dto.BookingConfirmationMessage;
import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.BookingResponse;
import com.booking.common.dto.InsufficientCapacityException;
import com.booking.core.config.RabbitMQConfig;
import com.booking.core.domain.Booking;
import com.booking.core.domain.Event;
import com.booking.core.domain.TicketTier;
import com.booking.core.domain.User;
import com.booking.core.repository.BookingRepository;
import com.booking.core.repository.TicketTierRepository;
import com.booking.core.repository.UserRepository;
import com.booking.core.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private TicketTierRepository ticketTierRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private StringRedisTemplate redisTemplate; // 👈 Injected Redis Mock

    @Mock
    private ValueOperations<String, String> valueOperations; // 👈 Injected ValueOperations Mock

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User testUser;
    private TicketTier testTier;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("john.doe@example.com");
        testUser.setFullName("John Doe");

        testEvent = new Event();
        testEvent.setId(10L);
        testEvent.setName("Coldplay Music of the Spheres");

        testTier = new TicketTier();
        testTier.setId(100L);
        testTier.setName("VIP Lounge");
        testTier.setPrice(new BigDecimal("250.00"));
        testTier.setTotalCapacity(10);
        testTier.setAvailableCapacity(10);
        testTier.setEvent(testEvent);

        // Mock Redisson Lock
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            lenient().when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // Mock TransactionTemplate
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        // 🛡️ Mock Redis for markRecentWrite()
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("reserveTickets: Should successfully reserve tickets when capacity is available")
    void reserveTickets_Success() {
        BookingRequest request = BookingRequest.builder()
                .ticketTierId(100L)
                .seatCount(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(testTier));

        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(500L);
            return b;
        });

        BookingResponse response = bookingService.reserveTickets(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getBookingId()).isEqualTo(500L);
        assertThat(response.getSeatCount()).isEqualTo(2);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("500.00");
        assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");

        assertThat(testTier.getAvailableCapacity()).isEqualTo(8);

        verify(ticketTierRepository, times(1)).save(testTier);
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    @DisplayName("reserveTickets: Should throw InsufficientCapacityException when requested seats exceed capacity")
    void reserveTickets_InsufficientCapacity_ThrowsException() {
        testTier.setAvailableCapacity(2);

        BookingRequest request = BookingRequest.builder()
                .ticketTierId(100L)
                .seatCount(5)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(testTier));

        assertThatThrownBy(() -> bookingService.reserveTickets(1L, request))
                .isInstanceOf(InsufficientCapacityException.class)
                .hasMessageContaining("Sold out or not enough seats");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("confirmBooking: Should confirm booking and publish message to RabbitMQ")
    void confirmBooking_Success() {
        Booking booking = new Booking();
        booking.setId(500L);
        booking.setUser(testUser);
        booking.setTicketTier(testTier);
        booking.setSeatCount(2);
        booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
        booking.setReservedUntil(Instant.now().plusSeconds(600));

        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.confirmBooking(500L, 1L);

        assertThat(response.getStatus()).isEqualTo("CONFIRMED");

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.BOOKING_EXCHANGE),
                eq(RabbitMQConfig.BOOKING_ROUTING_KEY),
                any(BookingConfirmationMessage.class)
        );
    }

    @Test
    @DisplayName("confirmBooking: Should throw AccessDeniedException and NOT publish to RabbitMQ on IDOR attack")
    void confirmBooking_UnauthorizedUser_ThrowsAccessDeniedException() {
        Booking booking = new Booking();
        booking.setId(500L);
        booking.setUser(testUser); // User ID = 1L
        booking.setTicketTier(testTier);
        booking.setSeatCount(2);
        booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
        booking.setReservedUntil(Instant.now().plusSeconds(600));

        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(500L, 999L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to confirm this booking");

        assertThat(booking.getStatus()).isEqualTo(Booking.BookingStatus.PENDING_PAYMENT);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}