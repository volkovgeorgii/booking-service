package com.obank.booking.integration;

import com.obank.booking.BaseIntegrationTest;
import com.obank.booking.domain.ReservationStatus;
import com.obank.booking.web.dto.CreateEventRequest;
import com.obank.booking.web.dto.EventResponse;
import com.obank.booking.web.dto.ReservationResponse;
import com.obank.booking.web.dto.ReserveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingIdempotencyIT extends BaseIntegrationTest {

    @Test
    void repeatedReserveWithSameKey_returnsSameReservationNoDuplicate() {
        UUID eventId = createEvent();
        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        ReservationResponse first  = reserveSeat(eventId, "1", userId, idempotencyKey);
        ReservationResponse second = reserveSeat(eventId, "1", userId, idempotencyKey);
        ReservationResponse third  = reserveSeat(eventId, "1", userId, idempotencyKey);

        assertThat(first.reservationId()).isEqualTo(second.reservationId());
        assertThat(first.reservationId()).isEqualTo(third.reservationId());

        var userReservations = restTemplate()
                .getForObject("/api/users/" + userId + "/reservations", ReservationResponse[].class);
        assertThat(userReservations).hasSize(1);
    }

    @Test
    void repeatedPayWithSameKey_returnsSameReservationNoDuplicate() {
        UUID eventId = createEvent();
        UUID userId = UUID.randomUUID();

        ReservationResponse reserved = reserveSeat(eventId, "1", userId, UUID.randomUUID().toString());

        String payKey = UUID.randomUUID().toString();
        ReservationResponse first  = payReservation(reserved.reservationId(), payKey);
        ReservationResponse second = payReservation(reserved.reservationId(), payKey);

        assertThat(first.reservationId()).isEqualTo(second.reservationId());
        assertThat(first.status()).isEqualTo(ReservationStatus.PAID);
        assertThat(second.status()).isEqualTo(ReservationStatus.PAID);
    }

    private UUID createEvent() {
        var resp = restTemplate().postForObject("/api/events",
                new CreateEventRequest("Concert-" + UUID.randomUUID(), "CONCERT", 10),
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

    private ReservationResponse payReservation(UUID reservationId, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Idempotency-Key", key);
        return restTemplate().postForObject("/api/reservations/" + reservationId + "/pay",
                new HttpEntity<>(null, headers), ReservationResponse.class);
    }
}
