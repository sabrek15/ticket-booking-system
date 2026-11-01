package com.booking.core.repository;

import com.booking.core.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);
    List<Booking> findByTicketTierIdAndStatus(Long TicketTierId, Booking.BookingStatus status);
}
