package com.obank.booking.integration;

import com.obank.booking.BaseIntegrationTest;
import com.obank.booking.domain.ReservationStatus;
import com.obank.booking.web.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingFlowIT extends BaseIntegrationTest {

    @Test
    void fullHappyPath_reserve_pay_cancel() {
        UUID eventId = createEvent(5);
        UUID userId = UUID.randomUUID();

        AvailabilityResponse before = getAvailability(eventId);
        assertThat(before.availableCount()).isEqualTo(5);

        ReservationResponse reserved = reserveSeat(eventId, "3", userId, UUID.randomUUID().toString());
        assertThat(reserved.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reserved.seatNumber()).isEqualTo("3");
        assertThat(reserved.reservedUntil()).isNotNull();

        AvailabilityResponse afterReserve = getAvailability(eventId);
        assertThat(afterReserve.availableCount()).isEqualTo(4);

        ReservationResponse paid = payReservation(reserved.reservationId(), UUID.randomUUID().toString());
        assertThat(paid.status()).isEqualTo(ReservationStatus.PAID);
        assertThat(paid.paidAt()).isNotNull();

        ReservationResponse cancelled = cancelReservation(paid.reservationId());
        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isNotNull();

        AvailabilityResponse afterCancel = getAvailability(eventId);
        assertThat(afterCancel.availableCount()).isEqualTo(5);

        ReservationResponse[] history = restTemplate()
                .getForObject("/api/users/" + userId + "/reservations", ReservationResponse[].class);
        assertThat(history).hasSize(1);
        assertThat(history[0].reservationId()).isEqualTo(cancelled.reservationId());
    }

    @Test
    void cancelReserved_seatBecomesAvailable() {
        UUID eventId = createEvent(1);
        UUID userId = UUID.randomUUID();

        ReservationResponse reserved = reserveSeat(eventId, "1", userId, UUID.randomUUID().toString());
        assertThat(getAvailability(eventId).availableCount()).isZero();

        cancelReservation(reserved.reservationId());

        assertThat(getAvailability(eventId).availableCount()).isEqualTo(1);

        ReservationResponse newReserved = reserveSeat(eventId, "1", UUID.randomUUID(), UUID.randomUUID().toString());
        assertThat(newReserved.status()).isEqualTo(ReservationStatus.RESERVED);
    }

    private UUID createEvent(int seats) {
        var resp = restTemplate().postForObject("/api/events",
                new CreateEventRequest("Concert-" + UUID.randomUUID(), "CONCERT", seats),
                EventResponse.class);
        assertThat(resp).isNotNull();
        return resp.id();
    }

    private AvailabilityResponse getAvailability(UUID eventId) {
        return restTemplate().getForObject(
                "/api/events/" + eventId + "/availability", AvailabilityResponse.class);
    }

    private ReservationResponse reserveSeat(UUID eventId, String seat, UUID userId, String key) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Idempotency-Key", key);
        return restTemplate().postForObject("/api/reservations/reserve",
                new HttpEntity<>(new ReserveRequest(eventId, seat, userId), h),
                ReservationResponse.class);
    }

    private ReservationResponse payReservation(UUID reservationId, String key) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Idempotency-Key", key);
        return restTemplate().postForObject("/api/reservations/" + reservationId + "/pay",
                new HttpEntity<>(null, h), ReservationResponse.class);
    }

    private ReservationResponse cancelReservation(UUID reservationId) {
        return restTemplate().postForObject("/api/reservations/" + reservationId + "/cancel",
                null, ReservationResponse.class);
    }
}
