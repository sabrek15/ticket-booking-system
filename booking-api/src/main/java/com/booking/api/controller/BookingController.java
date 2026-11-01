package com.booking.api.controller;

import com.booking.common.dto.BookingRequest;
import com.booking.common.dto.BookingResponse;
import com.booking.core.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookingService;

    @PostMapping("/reserve")
    public ResponseEntity<BookingResponse> reserveTickets(@Valid @RequestBody BookingRequest request) {
        BookingResponse response = bookingService.reserveTickets(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
