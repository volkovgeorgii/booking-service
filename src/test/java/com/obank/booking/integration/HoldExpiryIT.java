package com.obank.booking.integration;

import com.obank.booking.BaseIntegrationTest;
import com.obank.booking.domain.ReservationStatus;
import com.obank.booking.service.ReservationExpiryService;
import com.obank.booking.web.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

class HoldExpiryIT extends BaseIntegrationTest {

    @Autowired
    private ReservationExpiryService reservationExpiryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void expiredReservation_seatBecomesAvailableForNewReservation() {
        UUID eventId = createEvent();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        ReservationResponse reserved = reserveSeat(eventId, "1", userId1, UUID.randomUUID().toString());
        assertThat(reserved.status()).isEqualTo(ReservationStatus.RESERVED);

        jdbcTemplate.update("UPDATE reservations SET reserved_until = NOW() - INTERVAL '1 minute' WHERE id = ?",
                reserved.reservationId());

        reservationExpiryService.processExpiredReservations();

        ReservationResponse expired = getReservation(reserved.reservationId());
        assertThat(expired.status()).isEqualTo(ReservationStatus.EXPIRED);

        AvailabilityResponse avail = getAvailability(eventId);
        assertThat(avail.availableCount()).isEqualTo(1);

        ReservationResponse newReserved = reserveSeat(eventId, "1", userId2, UUID.randomUUID().toString());
        assertThat(newReserved.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(newReserved.reservationId()).isNotEqualTo(reserved.reservationId());
    }

    @Test
    void payExpiredReservation_returns422() {
        UUID eventId = createEvent();
        ReservationResponse reserved = reserveSeat(eventId, "1", UUID.randomUUID(), UUID.randomUUID().toString());

        jdbcTemplate.update("UPDATE reservations SET reserved_until = NOW() - INTERVAL '1 minute' WHERE id = ?",
                reserved.reservationId());
        reservationExpiryService.processExpiredReservations();

        var headers = new HttpHeaders();
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());

        try {
            restTemplate().postForEntity(
                    "/api/reservations/" + reserved.reservationId() + "/pay",
                    new HttpEntity<>(null, headers), String.class);
            org.junit.jupiter.api.Assertions.fail("Expected 422 but got 2xx");
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
        }
    }

    private UUID createEvent() {
        var resp = restTemplate().postForObject("/api/events",
                new CreateEventRequest("Event-" + UUID.randomUUID(), "THEATRE", 1),
                EventResponse.class);
        assertThat(resp).isNotNull();
        return resp.id();
    }

    private ReservationResponse reserveSeat(UUID eventId, String seat, UUID userId, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", key);
        return restTemplate().postForObject("/api/reservations/reserve",
                new HttpEntity<>(new ReserveRequest(eventId, seat, userId), headers),
                ReservationResponse.class);
    }

    private ReservationResponse getReservation(UUID reservationId) {
        return restTemplate().getForObject("/api/reservations/" + reservationId, ReservationResponse.class);
    }

    private AvailabilityResponse getAvailability(UUID eventId) {
        return restTemplate().getForObject(
                "/api/events/" + eventId + "/availability", AvailabilityResponse.class);
    }
}
