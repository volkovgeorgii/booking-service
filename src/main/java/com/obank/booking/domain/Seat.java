package com.obank.booking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.FREE;

    @Column(name = "reserved_by")
    private UUID reservedBy;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    public Seat(UUID eventId, String seatNumber) {
        this.eventId = eventId;
        this.seatNumber = seatNumber;
    }

    public void markReserved(UUID reservationId, Instant reservedUntil) {
        this.status = SeatStatus.RESERVED;
        this.reservedBy = reservationId;
        this.reservedUntil = reservedUntil;
    }

    public void markSold() {
        this.status = SeatStatus.SOLD;
    }

    public void release() {
        this.status = SeatStatus.FREE;
        this.reservedBy = null;
        this.reservedUntil = null;
    }
}
