package com.obank.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.obank.booking.repository.ReservationRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationExpiryService {

    private static final String LEADER_LOCK_KEY = "expiry-job:leader";

    @Value("${booking.expiry.check-interval-ms:30000}")
    private long checkIntervalMs;

    private final ReservationRepository reservationRepository;
    private final ReservationPersistenceService persistence;
    private final EventService eventService;
    private final RedisDistributedLock distributedLock;

    @Scheduled(fixedDelayString = "${booking.expiry.check-interval-ms:30000}")
    public void processExpiredReservations() {
        long lockTtlMs = Math.max(checkIntervalMs - 1000, 5000);
        Optional<String> leaderToken = distributedLock.tryAcquire(LEADER_LOCK_KEY, lockTtlMs);
        if (leaderToken.isEmpty()) {
            log.debug("Expiry job skipped — another pod is the leader");
            return;
        }

        try {
            runExpiry();
        } finally {
            distributedLock.release(LEADER_LOCK_KEY, leaderToken.get());
        }
    }

    private void runExpiry() {
        List<UUID> expiredIds = reservationRepository.findExpiredReservationIds(Instant.now());
        if (expiredIds.isEmpty()) return;

        log.info("Expiring {} stale reservations", expiredIds.size());
        for (UUID reservationId : expiredIds) {
            try {
                persistence.expireReservation(reservationId);
                reservationRepository.findById(reservationId)
                        .ifPresent(r -> eventService.evictAvailabilityCache(r.getEventId()));
            } catch (Exception e) {
                log.error("Failed to expire reservation {}: {}", reservationId, e.getMessage());
            }
        }
    }
}
