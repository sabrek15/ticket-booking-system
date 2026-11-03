package com.booking.core.service;

import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.BookingResponse;

public interface BookingService {
    BookingResponse reserveTickets(BookingRequest request);
    BookingResponse confirmBooking(Long bookingId);
    int releaseExpiredBookings();
}
