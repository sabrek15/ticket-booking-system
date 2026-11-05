package com.booking.core.service;

import com.booking.common.dto.EventCreateRequest;
import com.booking.common.dto.EventResponse;
import com.booking.common.dto.TicketTierCreateRequest;
import com.booking.common.dto.TicketTierResponse;

import java.util.List;

public interface EventService {
    EventResponse createEvent(EventCreateRequest request);
    TicketTierResponse addTicketTier(Long eventId, TicketTierCreateRequest request);
    List<EventResponse> getAllPublishedEvents();
    EventResponse getEventById(Long eventId);
    EventResponse publishEvent(Long eventId);
}
