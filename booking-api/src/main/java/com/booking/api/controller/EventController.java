package com.booking.api.controller;

import com.booking.common.dto.EventCreateRequest;
import com.booking.common.dto.EventResponse;
import com.booking.common.dto.TicketTierCreateRequest;
import com.booking.common.dto.TicketTierResponse;
import com.booking.core.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllPublishedEvents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @PostMapping("/{id}/tiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TicketTierResponse> addTier(
            @PathVariable Long id,
            @Valid @RequestBody TicketTierCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.addTicketTier(id, request));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EventResponse> publishEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.publishEvent(id));
    }
}
