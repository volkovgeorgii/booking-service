package com.obank.booking.web.dto;

import com.obank.booking.domain.Reservation;
import com.obank.booking.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID eventId,
        String seatNumber,
        UUID userId,
        ReservationStatus status,
        Instant reservedUntil,
        Instant paidAt,
        Instant cancelledAt,
        Instant createdAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(), r.getEventId(), r.getSeatNumber(), r.getUserId(),
                r.getStatus(), r.getReservedUntil(), r.getPaidAt(),
                r.getCancelledAt(), r.getCreatedAt());
    }
}
