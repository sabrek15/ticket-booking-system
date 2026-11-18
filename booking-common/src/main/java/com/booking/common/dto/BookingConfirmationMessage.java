package com.booking.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationMessage implements Serializable {
    private Long bookingId;
    private String customerEmail;
    private String customerName;
    private String eventName;
    private String tierName;
    private int seatCount;
    private BigDecimal totalPrice;
    private Instant confirmedAt;
}
