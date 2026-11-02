package com.booking.api.controller;

import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.BookingResponse;
import com.booking.common.dto.DuplicateRequestException;
import com.booking.core.service.BookingService;
import com.booking.core.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookingService;
    private final IdempotencyService idempotencyService;

    public BookingController(BookingService bookingService, IdempotencyService idempotencyService) {
        this.bookingService = bookingService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/reserve")
    public ResponseEntity<BookingResponse> reserveTickets(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingRequest request
    ) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            boolean acquired = idempotencyService.acquireLock(idempotencyKey, Duration.ofMinutes(2));
            if (!acquired) {
                throw new DuplicateRequestException(
                        "Duplicate request detected with Idempotency-Key: " + idempotencyKey
                );
            }
        }

        BookingResponse response = bookingService.reserveTickets(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
