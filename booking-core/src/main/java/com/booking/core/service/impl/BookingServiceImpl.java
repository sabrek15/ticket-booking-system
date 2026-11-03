package com.booking.core.service.impl;

import com.booking.common.dto.*;
import com.booking.core.domain.Booking;
import com.booking.core.domain.TicketTier;
import com.booking.core.domain.User;
import com.booking.core.repository.BookingRepository;
import com.booking.core.repository.TicketTierRepository;
import com.booking.core.repository.UserRepository;
import com.booking.core.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final TicketTierRepository ticketTierRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    @Override
    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            propagation = Propagation.REQUIRED,
            timeout = 5
    )
    public BookingResponse reserveTickets(BookingRequest request) {
        log.info("Processing reservation request for user: {}, tier: {}, seats: {}",
                request.getUserId(), request.getTicketTierId(), request.getSeatCount());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with Id: " + request.getUserId()));

        TicketTier tier = ticketTierRepository.findByIdWithPessimisticLock(request.getTicketTierId())
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
    }

    @Override
    @Transactional
    public BookingResponse confirmBooking(Long bookingId) {
        log.info("Processing confirmation for booking ID: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking Not Found with ID: " + bookingId));

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
}
