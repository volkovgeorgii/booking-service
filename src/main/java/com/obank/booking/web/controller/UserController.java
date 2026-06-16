package com.obank.booking.web.controller;

import com.obank.booking.service.ReservationService;
import com.obank.booking.web.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final ReservationService reservationService;

    @GetMapping("/{userId}/reservations")
    public List<ReservationResponse> getUserReservations(@PathVariable UUID userId) {
        return reservationService.getUserReservations(userId);
    }
}
