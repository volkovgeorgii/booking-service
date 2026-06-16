package com.obank.booking.repository;

import com.obank.booking.domain.Seat;
import com.obank.booking.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.eventId = :eventId AND s.seatNumber = :seatNumber")
    Optional<Seat> findByEventIdAndSeatNumberForUpdate(
            @Param("eventId") UUID eventId,
            @Param("seatNumber") String seatNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") UUID id);

    List<Seat> findByEventIdAndStatusOrderBySeatNumber(UUID eventId, SeatStatus status);
}
