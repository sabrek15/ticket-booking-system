package com.booking.core.listener;

import com.booking.core.domain.event.BookingConfirmedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class BookingNotificationListener {

    @Async("taskexecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingConfirmed(BookingConfirmedEvent event) {
        log.info("📧 [Thread: {}] Starting email notification for booking ID: {}",
                Thread.currentThread().getName(), event.getBookingId());

        try {
            Thread.sleep(2000);

            log.info("✅ [Thread: {}] Email sent to {}: 'Receipt for {} x {} tickets for event \"{}\". Total: ${}'",
                    Thread.currentThread().getName(),
                    event.getCustomerEmail(),
                    event.getSeatCount(),
                    event.getTierName(),
                    event.getEventName(),
                    event.getTotalPrice());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed sending booking confirmation email", e);
        }
    }
}
