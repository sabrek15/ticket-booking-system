package com.booking.api.scheduler;

import com.booking.core.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingCleanWorker {
    private static final Logger log = LoggerFactory.getLogger(BookingCleanWorker.class);
    private final BookingService bookingService;

    public BookingCleanWorker(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedRate = 30000)
    public void sweepExpiredReservation() {
        int releaseCount = bookingService.releaseExpiredBookings();
        if (releaseCount > 0) {
            log.info("🧹 Sweeper successfully restocked {} expired bookings.", releaseCount);
        }
    }
}
