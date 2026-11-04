package com.booking.api.controller;

import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.BookingResponse;
import com.booking.common.dto.DuplicateRequestException;
import com.booking.core.domain.User;
import com.booking.core.service.BookingService;
import com.booking.core.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal User currentUser,
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

        BookingResponse response = bookingService.reserveTickets(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long bookingId
    ) {
        BookingResponse response = bookingService.confirmBooking(bookingId, currentUser.getId());
        return ResponseEntity.ok(response);
    }
}
