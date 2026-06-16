package com.obank.booking.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReserveRequest(
        @NotNull UUID eventId,
        @NotBlank String seatId,
        @NotNull UUID userId
) {}
