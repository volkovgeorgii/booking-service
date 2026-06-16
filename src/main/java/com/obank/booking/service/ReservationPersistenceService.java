package com.obank.booking.service;

import com.obank.booking.domain.Reservation;
import com.obank.booking.domain.ReservationStatus;
import com.obank.booking.domain.Seat;
import com.obank.booking.domain.SeatStatus;
import com.obank.booking.exception.ReservationNotFoundException;
import com.obank.booking.exception.InvalidStateException;
import com.obank.booking.exception.SeatUnavailableException;
import com.obank.booking.repository.ReservationRepository;
import com.obank.booking.repository.SeatRepository;
import com.obank.booking.web.dto.ReservationResponse;
import com.obank.booking.web.dto.ReserveRequest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationPersistenceService {

    @Value("${booking.hold.duration-minutes:10}")
    private int holdDurationMinutes;

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final MeterRegistry meterRegistry;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CacheEvict(value = "availability", key = "#request.eventId()")
    public ReservationResponse reserveSeat(ReserveRequest request) {
        Seat seat = seatRepository
                .findByEventIdAndSeatNumberForUpdate(request.eventId(), request.seatId())
                .orElseThrow(() -> new SeatUnavailableException(
                        "Seat '%s' not found in event %s".formatted(request.seatId(), request.eventId())));

        if (seat.getStatus() != SeatStatus.FREE) {
            throw new SeatUnavailableException(
                    "Seat '%s' is not available (status: %s)".formatted(request.seatId(), seat.getStatus()));
        }

        Instant expiresAt = Instant.now().plus(holdDurationMinutes, ChronoUnit.MINUTES);

        Reservation reservation = new Reservation(
                request.eventId(), seat.getId(), request.seatId(),
                request.userId(), ReservationStatus.RESERVED, expiresAt);
        reservationRepository.save(reservation);

        seat.markReserved(reservation.getId(), expiresAt);
        seatRepository.save(seat);

        meterRegistry.counter("reservation.reserve.total").increment();
        return ReservationResponse.from(reservation);
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public ReservationResponse payReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new InvalidStateException(
                    "Cannot pay reservation with status: " + reservation.getStatus());
        }
        if (reservation.isExpired()) {
            throw new InvalidStateException(
                    "Cannot pay reservation — hold has expired at " + reservation.getReservedUntil());
        }

        reservation.pay();
        seatRepository.findByIdForUpdate(reservation.getSeatId()).ifPresent(Seat::markSold);

        meterRegistry.counter("reservation.pay.total").increment();
        return ReservationResponse.from(reservation);
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public ReservationResponse cancelReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return ReservationResponse.from(reservation);
        }
        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new InvalidStateException("Cannot cancel an expired reservation");
        }

        reservation.cancel();
        seatRepository.findByIdForUpdate(reservation.getSeatId()).ifPresent(Seat::release);

        meterRegistry.counter("reservation.cancel.total").increment();
        return ReservationResponse.from(reservation);
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public void expireReservation(UUID reservationId) {
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(reservation -> {
            if (reservation.getStatus() == ReservationStatus.RESERVED && reservation.isExpired()) {
                reservation.expire();
                seatRepository.findByIdForUpdate(reservation.getSeatId()).ifPresent(Seat::release);
                meterRegistry.counter("reservation.expired.total").increment();
            }
        });
    }
}
