package com.obank.booking.web.controller;

import com.obank.booking.service.EventService;
import com.obank.booking.web.dto.AvailabilityResponse;
import com.obank.booking.web.dto.CreateEventRequest;
import com.obank.booking.web.dto.EventResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse getAvailability(@PathVariable UUID id) {

        return eventService.getAvailability(id);
    }
}
