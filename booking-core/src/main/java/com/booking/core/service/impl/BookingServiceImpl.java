package com.booking.core.service.impl;

import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.BookingResponse;
import com.booking.common.dto.InsufficientCapacityException;
import com.booking.common.dto.ResourceNotFoundException;
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
}
