package com.obank.booking.repository;

import com.obank.booking.domain.Reservation;
import com.obank.booking.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT r.id FROM Reservation r WHERE r.status = 'RESERVED' AND r.reservedUntil < :now")
    List<UUID> findExpiredReservationIds(@Param("now") Instant now);

    boolean existsByIdAndStatus(UUID id, ReservationStatus status);
}
