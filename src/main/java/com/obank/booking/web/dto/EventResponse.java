package com.obank.booking.web.dto;

import com.obank.booking.domain.Event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String name,
        String type,
        int totalSeats,
        Instant createdAt
) {
    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getType(),
                e.getTotalSeats(), e.getCreatedAt());
    }
}
