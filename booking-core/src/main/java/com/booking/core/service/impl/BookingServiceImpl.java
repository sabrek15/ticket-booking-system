package com.booking.core.service.impl;

import com.booking.common.dto.*;
import com.booking.core.domain.Booking;
import com.booking.core.domain.TicketTier;
import com.booking.core.domain.User;
import com.booking.core.domain.event.BookingConfirmedEvent;
import com.booking.core.repository.BookingRepository;
import com.booking.core.repository.TicketTierRepository;
import com.booking.core.repository.UserRepository;
import com.booking.core.service.BookingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final TicketTierRepository ticketTierRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    @Override
    @CircuitBreaker(name = "redisLock", fallbackMethod = "fallbackToDatabaseLock")
    public BookingResponse reserveTickets(Long userId, BookingRequest request) {
        log.info("Processing reservation request for user ID: {}, tier: {}, seats: {}",
                userId, request.getTicketTierId(), request.getSeatCount());

        String lockKey = "lock:tier:" + request.getTicketTierId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean isLocked = false;

        try {
            isLocked = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (!isLocked) {
                log.warn("High contention: Could not aquire lock for tier {}",
                        request.getTicketTierId());
                throw new InsufficientCapacityException("System busy, Please try again");
            }

            return transactionTemplate.execute(status -> {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with Id: " + userId));

                TicketTier tier = ticketTierRepository.findById(request.getTicketTierId())
                        .orElseThrow(() -> new ResourceNotFoundException("Ticket tier not found with id: " + request.getTicketTierId()));

                if (tier.getAvailableCapacity() < request.getSeatCount()) {
                    log.warn("Insufficient tickets for tier: {}. Requested: {}, Available: {}",
                            tier.getId(), request.getSeatCount(), tier.getAvailableCapacity());
                    throw new InsufficientCapacityException(
                            String.format("Sold out or not enough seats. Requested: %d, Available: %d",
                                    request.getSeatCount(), tier.getAvailableCapacity()));
                }

                tier.setAvailableCapacity(tier.getAvailableCapacity() - request.getSeatCount());
                ticketTierRepository.save(tier);

                Booking booking = new Booking();
                booking.setUser(user);
                booking.setTicketTier(tier);
                booking.setSeatCount(request.getSeatCount());
                booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
                booking.setReservedUntil(Instant.now().plus(HOLD_DURATION));

                Booking savedBooking = bookingRepository.save(booking);

                BigDecimal totalPrice = tier.getPrice().multiply(BigDecimal.valueOf(request.getSeatCount()));

                log.info("Booking {} reserved successfully with status PENDING_PAYMENT.", savedBooking.getId());

                return BookingResponse.builder()
                        .bookingId(savedBooking.getId())
                        .userId(user.getId())
                        .ticketTierId(tier.getId())
                        .tierName(tier.getName())
                        .seatCount(savedBooking.getSeatCount())
                        .totalPrice(totalPrice)
                        .status(savedBooking.getStatus().name())
                        .reservedUntil(savedBooking.getReservedUntil())
                        .build();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while waiting for lock", e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released distributed lock for key: {}", lockKey);
            }
        }
    }

    @Override
    @Transactional(noRollbackFor = BookingExpiredException.class)
    public BookingResponse confirmBooking(Long bookingId, Long userId) {
        log.info("Processing confirmation for booking ID: {} by user ID: {}", bookingId, userId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking Not Found with ID: " + bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to confirm booking {} belonging to user {}",
                    userId, bookingId, booking.getUser().getId());
            throw new AccessDeniedException("You do not have permission to confirm this booking.");
        }

        if (booking.getStatus() == Booking.BookingStatus.EXPIRED) {
            throw new BookingExpiredException("Reservation time has expired. Your seats have been released.");
        }

        if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Booking is already confirmed and paid for.");
        }

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking has been cancelled.");
        }

        if (Instant.now().isAfter(booking.getReservedUntil())) {
            booking.setStatus(Booking.BookingStatus.EXPIRED);

            TicketTier tier = booking.getTicketTier();
            tier.setAvailableCapacity(tier.getAvailableCapacity() + booking.getSeatCount());

            throw new BookingExpiredException("Reservation time has expired. Your seats have been released.");
        }

        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        Booking savedBooking = bookingRepository.save(booking);

        BigDecimal totalPrice = savedBooking.getTicketTier().getPrice()
                .multiply(BigDecimal.valueOf(savedBooking.getSeatCount()));

        log.info("Booking {} confirmed. Emitted BookingConfirmedEvent.", savedBooking.getId());

        eventPublisher.publishEvent(new BookingConfirmedEvent(
                this,
                savedBooking.getId(),
                savedBooking.getUser().getEmail(),
                savedBooking.getUser().getFullName(),
                savedBooking.getTicketTier().getEvent().getName(),
                savedBooking.getTicketTier().getName(),
                savedBooking.getSeatCount(),
                totalPrice
        ));

        return BookingResponse.builder()
                .bookingId(savedBooking.getId())
                .userId(savedBooking.getUser().getId())
                .ticketTierId(savedBooking.getTicketTier().getId())
                .tierName(savedBooking.getTicketTier().getName())
                .seatCount(savedBooking.getSeatCount())
                .totalPrice(totalPrice)
                .status(savedBooking.getStatus().name())
                .reservedUntil(savedBooking.getReservedUntil())
                .build();
    }

    @Override
    @Transactional
    public int releaseExpiredBookings() {
        Instant now = Instant.now();
        List<Booking> expiredBookings = bookingRepository.findExpiredBookings(now);

        if (expiredBookings.isEmpty()) {
            return 0;
        }

        log.info("Found {} expired reservation to release.", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            booking.setStatus(Booking.BookingStatus.EXPIRED);

            TicketTier tier = booking.getTicketTier();
            tier.setAvailableCapacity(tier.getAvailableCapacity() + booking.getSeatCount());

            log.info("Restocked {} seats to tier: {} from expired booking: {}",
                    booking.getSeatCount(), tier.getName(), booking.getId());
        }

        return expiredBookings.size();
    }

    public BookingResponse fallbackToDatabaseLock(Long userId, BookingRequest request, Throwable t) {
        log.warn("REDIS CIRCUIT OPEN! Reason: '{}'. Falling back to Postgres Pessimesstic Lock for tier: {}",
                t.getMessage(), request.getTicketTierId());

        return transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
            TicketTier tier = ticketTierRepository.findByIdWithPessimisticLock(request.getTicketTierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket Tier not found with ID: " + request.getTicketTierId()));

            if (tier.getAvailableCapacity() < request.getSeatCount()) {
                throw new InsufficientCapacityException(
                        String.format("Sold out or not enough seats. Requested: %d, Available: %d",
                                request.getSeatCount(), tier.getAvailableCapacity()));
            }

            tier.setAvailableCapacity(tier.getAvailableCapacity() - request.getSeatCount());
            ticketTierRepository.save(tier);

            Booking booking = new Booking();
            booking.setUser(user);
            booking.setTicketTier(tier);
            booking.setSeatCount(request.getSeatCount());
            booking.setStatus(Booking.BookingStatus.PENDING_PAYMENT);
            booking.setReservedUntil(Instant.now().plus(HOLD_DURATION));

            Booking savedBooking = bookingRepository.save(booking);
            BigDecimal totalPrice = tier.getPrice().multiply(BigDecimal.valueOf(request.getSeatCount()));

            log.info("🛡️ Fallback successfully booked {} seats using PostgreSQL row lock. Booking ID: {}",
                    savedBooking.getSeatCount(), savedBooking.getId());

            return BookingResponse.builder()
                    .bookingId(savedBooking.getId())
                    .userId(user.getId())
                    .ticketTierId(tier.getId())
                    .tierName(tier.getName())
                    .seatCount(savedBooking.getSeatCount())
                    .totalPrice(totalPrice)
                    .status(savedBooking.getStatus().name())
                    .reservedUntil(savedBooking.getReservedUntil())
                    .build();
        });
    }
}
