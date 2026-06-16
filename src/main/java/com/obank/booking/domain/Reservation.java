package com.obank.booking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Reservation(UUID eventId, UUID seatId, String seatNumber, UUID userId,
                       ReservationStatus status, Instant reservedUntil) {
        this.eventId = eventId;
        this.seatId = seatId;
        this.seatNumber = seatNumber;
        this.userId = userId;
        this.status = status;
        this.reservedUntil = reservedUntil;
    }

    public boolean isExpired() {
        return reservedUntil != null && Instant.now().isAfter(reservedUntil);
    }

    public void pay() {
        this.status = ReservationStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }
}
