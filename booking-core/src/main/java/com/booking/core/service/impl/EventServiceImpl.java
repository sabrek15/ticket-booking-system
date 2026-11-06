package com.booking.core.service.impl;

import com.booking.common.dto.*;
import com.booking.core.domain.Event;
import com.booking.core.domain.TicketTier;
import com.booking.core.repository.EventRepository;
import com.booking.core.repository.TicketTierRepository;
import com.booking.core.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final TicketTierRepository ticketTierRepository;

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        log.info("Creating new event: {}", request.getName());

        Event event = new Event();
        event.setName(request.getName());
        event.setDescription(request.getDescription());
        event.setVenue(request.getVenue());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setStatus(Event.EventStatus.DRAFT);

        Event savedEvent = eventRepository.save(event);
        return mapToEventResponse(savedEvent);
    }

    @Override
    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public TicketTierResponse addTicketTier(Long eventId, TicketTierCreateRequest request) {
        log.info("Adding ticket tier: {} to event ID: {}", request.getName(), eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event non found with ID: " + eventId));

        TicketTier tier = new TicketTier();
        tier.setName(request.getName());
        tier.setPrice(request.getPrice());
        tier.setTotalCapacity(request.getCapacity());
        tier.setAvailableCapacity(request.getCapacity());
        tier.setEvent(event);

        TicketTier savedTier = ticketTierRepository.save(tier);

        return TicketTierResponse.builder()
                .id(savedTier.getId())
                .name(savedTier.getName())
                .price(savedTier.getPrice())
                .totalCapacity(savedTier.getTotalCapacity())
                .availableCapacity(savedTier.getAvailableCapacity())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "events", key = "all")
    public List<EventResponse> getAllPublishedEvents() {
        log.info("🔥 Cache MISS: Fetching all published events from PostgreSQL");
        return eventRepository.findByStatus(Event.EventStatus.PUBLISHED)
                .stream()
                .map(this::mapToEventResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "events", key = "#eventId")
    public EventResponse getEventById(Long eventId) {
        log.info("🔥 Cache MISS: Fetching event ID: {} from PostgreSQL", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        return mapToEventResponse(event);
    }

    @Override
    @Transactional
    @CacheEvict(value = "events", allEntries = true)
    public EventResponse publishEvent(Long eventId) {
        log.info("Publishing event ID: {}. Evicting cache...", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + eventId));

        event.setStatus(Event.EventStatus.PUBLISHED);
        Event saved = eventRepository.save(event);
        return mapToEventResponse(saved);
    }

    public EventResponse mapToEventResponse(Event event) {
        List<TicketTierResponse> tiers = event.getTicketTiers().stream()
                .map(tier -> TicketTierResponse.builder()
                        .id(tier.getId())
                        .name(tier.getName())
                        .price(tier.getPrice())
                        .totalCapacity(tier.getTotalCapacity())
                        .availableCapacity(tier.getAvailableCapacity())
                        .build()
                )
                .toList();

        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .venue(event.getVenue())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus().name())
                .ticketTiers(tiers)
                .build();
    }
}
