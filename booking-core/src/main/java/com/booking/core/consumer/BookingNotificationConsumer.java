package com.booking.core.consumer;

import com.booking.common.dto.BookingConfirmationMessage;
import com.booking.core.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BookingNotificationConsumer {

    @RabbitListener(queues = RabbitMQConfig.BOOKING_QUEUE)
    public void processBookingConfirmation(BookingConfirmationMessage message) {
        log.info("📩 [RabbitMQ Consumer] Received message for booking ID: {}", message.getBookingId());

        try {
            Thread.sleep(1000);

            log.info("✅ [RabbitMQ Consumer] Sent ticket email to {} for event '{}'. Total: ${}",
                    message.getCustomerEmail(),
                    message.getEventName(),
                    message.getTotalPrice());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erroe processing booking message", e);
        }
    }
}
