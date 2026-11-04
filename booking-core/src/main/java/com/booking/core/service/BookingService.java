package com.booking.core.service;

import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.BookingResponse;

public interface BookingService {
    BookingResponse reserveTickets(Long userId, BookingRequest request);
    BookingResponse confirmBooking(Long bookingId, Long userId);
    int releaseExpiredBookings();
}
