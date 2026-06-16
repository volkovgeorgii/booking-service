package com.obank.booking.web.dto;

import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
        UUID eventId,
        int totalSeats,
        long availableCount,
        List<String> availableSeatNumbers
) {}
