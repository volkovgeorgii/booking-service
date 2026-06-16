package com.obank.booking.service;

import com.obank.booking.exception.ReservationNotFoundException;
import com.obank.booking.repository.ReservationRepository;
import com.obank.booking.web.dto.ReservationResponse;
import com.obank.booking.web.dto.ReserveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

    @Value("${booking.lock.ttl-ms:10000}")
    private long lockTtlMs;

    private final ReservationPersistenceService persistence;
    private final IdempotencyService idempotency;
    private final RedisDistributedLock distributedLock;
    private final ReservationRepository reservationRepository;

    public ReservationResponse reserveSeat(ReserveRequest request, String idempotencyKey) {
        var cached = idempotency.findCached(idempotencyKey, "RESERVE");
        if (cached.isPresent()) return cached.get();

        String lockKey = "lock:seat:%s:%s".formatted(request.eventId(), request.seatId());

        return distributedLock.withLock(lockKey, lockTtlMs, () -> {
            var cachedInLock = idempotency.findCached(idempotencyKey, "RESERVE");
            if (cachedInLock.isPresent()) return cachedInLock.get();
            var dbCached = idempotency.findInDb(idempotencyKey, "RESERVE");
            if (dbCached.isPresent()) return dbCached.get();

            ReservationResponse response = persistence.reserveSeat(request);
            idempotency.save(idempotencyKey, "RESERVE", response.reservationId(), 200, response);
            return response;
        });
    }

    public ReservationResponse payReservation(UUID reservationId, String idempotencyKey) {
        var cached = idempotency.findCached(idempotencyKey, "PAY");
        if (cached.isPresent()) return cached.get();

        ReservationResponse response = persistence.payReservation(reservationId);
        idempotency.save(idempotencyKey, "PAY", reservationId, 200, response);
        return response;
    }

    public ReservationResponse cancelReservation(UUID reservationId) {
        return persistence.cancelReservation(reservationId);
    }

    public ReservationResponse getReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

    public List<ReservationResponse> getUserReservations(UUID userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }
}
