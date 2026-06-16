package com.obank.booking.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateEventRequest(
        @NotBlank String name,
        @NotBlank String type,
        @Positive int totalSeats
) {}
