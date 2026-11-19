package com.booking.core.consumer;

import com.booking.common.dto.BookingConfirmationMessage;
import com.booking.core.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNotificationConsumer {

    private final StringRedisTemplate redisTemplate;

    public static final String DEDUPLICATION_KEY_PREFIX = "msg:processed:booking:";
    public static final Duration RETENTION_PERIOD = Duration.ofDays(7);

    @RabbitListener(queues = RabbitMQConfig.BOOKING_QUEUE)
    public void processBookingConfirmation(BookingConfirmationMessage message) {
        String deduplicationKey = DEDUPLICATION_KEY_PREFIX + message.getBookingId();

        Boolean isFirstDelivery = redisTemplate.opsForValue()
                .setIfAbsent(deduplicationKey, "PROCESSED", RETENTION_PERIOD);

        if (!Boolean.TRUE.equals(isFirstDelivery)) {
            log.warn("⚠️ [RabbitMQ Consumer] DUPLICATE MESSAGE DETECTED for booking ID: {}. Skipping execution to prevent duplicate email",
                    message.getBookingId());
            return;
        }

        log.info("📩 [RabbitMQ Consumer] Received message for booking ID: {}", message.getBookingId());

        try {
            Thread.sleep(1000);

            log.info("✅ [RabbitMQ Consumer] Sent ticket email to {} for event '{}'. Total: ${}",
                    message.getCustomerEmail(),
                    message.getEventName(),
                    message.getTotalPrice());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            redisTemplate.delete(deduplicationKey);
            log.error("Error processing booking message", e);
            throw new RuntimeException("Notification processing failed, triggering retry", e);
        }
    }
}
