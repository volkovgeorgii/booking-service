package com.obank.booking.web.controller;

import com.obank.booking.service.ReservationService;
import com.obank.booking.web.dto.ReservationResponse;
import com.obank.booking.web.dto.ReserveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveSeat(
            @Valid @RequestBody ReserveRequest request,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        return reservationService.reserveSeat(request, idempotencyKey);
    }

    @PostMapping("/{id}/pay")
    public ReservationResponse payReservation(
            @PathVariable UUID id,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey) {
        return reservationService.payReservation(id, idempotencyKey);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancelReservation(@PathVariable UUID id) {
        return reservationService.cancelReservation(id);
    }

    @GetMapping("/{id}")
    public ReservationResponse getReservation(@PathVariable UUID id) {
        return reservationService.getReservation(id);
    }
}
