package com.booking.api;

import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.InsufficientCapacityException;
import com.booking.core.domain.Event;
import com.booking.core.domain.TicketTier;
import com.booking.core.domain.User;
import com.booking.core.repository.BookingRepository;
import com.booking.core.repository.EventRepository;
import com.booking.core.repository.TicketTierRepository;
import com.booking.core.repository.UserRepository;
import com.booking.core.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class BookingConcurrencyTest {
    @Autowired
    private BookingService bookingService;

    @Autowired
    private TicketTierRepository ticketTierRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    private Long testUserId;
    private Long testTierId;

    @BeforeEach
    void setup() {
        bookingRepository.deleteAll();
        ticketTierRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFullName("Test User");
        user.setEmail("stress.test@example.com");
        user.setMembershipTier(User.MembershipTier.STANDARD);
        user = userRepository.save(user);
        this.testUserId = user.getId();

        Event event = new Event();
        event.setName("Flash Sale Concert");
        event.setVenue("Tech Arena");
        event.setStartTime(Instant.now().plus(10, ChronoUnit.DAYS));
        event.setEndTime(Instant.now().plus(10, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS));
        event = eventRepository.save(event);

        TicketTier tier = new TicketTier();
        tier.setEvent(event);
        tier.setName("Front Row VIP");
        tier.setPrice(new BigDecimal("100.00"));
        tier.setTotalCapacity(5);
        tier.setAvailableCapacity(5); // Only 5 available!
        tier = ticketTierRepository.save(tier);
        this.testTierId = tier.getId();
    }

    @Test
    @DisplayName("50 Concurrent requests competing for 5 tickets: exactly 5 succed, 45 fail")
    void testConcurrentBookingsNeverOversell() throws InterruptedException {
        int totalThreads = 50;
        int requestedSeatsPerThread = 1;

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        CountDownLatch startGate = new CountDownLatch(1);

        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();

                    BookingRequest request = BookingRequest.builder()
                            .userId(testUserId)
                            .ticketTierId(testTierId)
                            .seatCount(requestedSeatsPerThread)
                            .build();

                    bookingService.reserveTickets(request);
                    successCount.incrementAndGet();
                } catch (InsufficientCapacityException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Unexpected exception: " + e.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();

        endGate.await();
        executor.shutdown();

        assertEquals(5, successCount.get(), "Expected exactly 5 successful bookings");

        assertEquals(45, failureCount.get(), "Expected exactly 45 failed bookings");

        TicketTier finalTierState = ticketTierRepository.findById(testTierId).orElseThrow();
        assertEquals(0, finalTierState.getAvailableCapacity(), "Remaining seats must be exactly 0");

        assertEquals(5, bookingRepository.count(), "Database should have exactly 5 booking records");

        System.out.println("✅ CONCURRENCY TEST PASSED: 5 Succeeded, 45 Blocked, 0 Oversold!");
    }
}
