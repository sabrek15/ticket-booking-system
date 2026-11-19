package com.booking.core.consumer;

import com.booking.common.dto.BookingConfirmationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingNotificationConsumerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private BookingNotificationConsumer consumer;

    private BookingConfirmationMessage testMessage;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        testMessage = BookingConfirmationMessage.builder()
                .bookingId(101L)
                .customerEmail("john.doe@example.com")
                .customerName("John Doe")
                .eventName("Coldplay Concert")
                .tierName("VIP")
                .seatCount(2)
                .totalPrice(new BigDecimal("500.00"))
                .confirmedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("processBookingConfirmation: Should process successfully on first delivery")
    void processBookingConfirmation_FirstDelivery_Success() {
        when(valueOperations.setIfAbsent(eq("msg:processed:booking:101"), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(true);

        consumer.processBookingConfirmation(testMessage);

        verify(valueOperations, times(1))
                .setIfAbsent(eq("msg:processed:booking:101"), eq("PROCESSED"), any(Duration.class));
    }

    @Test
    @DisplayName("processBookingConfirmation: Should skip processing on duplicate redelivery")
    void processBookingConfirmation_DuplicateDelivery_Skipped() {
        when(valueOperations.setIfAbsent(eq("msg:processed:booking:101"), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(false);

        consumer.processBookingConfirmation(testMessage);

        verify(valueOperations, times(1))
                .setIfAbsent(eq("msg:processed:booking:101"), eq("PROCESSED"), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
    }
}