package com.booking.core.repository;

import com.booking.core.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByTicketTierIdAndStatus(Long TicketTierId, Booking.BookingStatus status);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.ticketTier
        WHERE b.status = 'PENDING_PAYMENT'
            AND b.reservedUntil < :now
    """)
    List<Booking> findExpiredBookings(@Param("now")Instant now);
}
