package com.booking.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long bookingId;
    private Long userId;
    private Long ticketTierId;
    private String tierName;
    private int seatCount;
    private BigDecimal totalPrice;
    private String status;
    private Instant reservedUntil;
}
