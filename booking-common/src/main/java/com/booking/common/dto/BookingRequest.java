package com.booking.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {
    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Ticket tier ID is required")
    private Long ticketTierId;

    @Min(value = 1, message = "Must reserve at least 1 ticket")
    private int seatCount;
}
