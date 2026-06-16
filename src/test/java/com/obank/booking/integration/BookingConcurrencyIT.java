package com.obank.booking.integration;

import com.obank.booking.BaseIntegrationTest;
import com.obank.booking.web.dto.AvailabilityResponse;
import com.obank.booking.web.dto.CreateEventRequest;
import com.obank.booking.web.dto.EventResponse;
import com.obank.booking.web.dto.ReservationResponse;
import com.obank.booking.web.dto.ReserveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConcurrencyIT extends BaseIntegrationTest {

    @Test
    void twentyConcurrentReserves_onlyOneSucceeds() throws InterruptedException {
        UUID eventId = createEventWithOneSeat();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        AtomicInteger success  = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final UUID userId = UUID.randomUUID();
            final String idemKey = UUID.randomUUID().toString();
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reserveSeat(eventId, "1", userId, idemKey);
                    success.incrementAndGet();
                } catch (HttpClientErrorException.Conflict e) {
                    conflict.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(success.get()).as("Exactly one reserve should succeed").isEqualTo(1);
        assertThat(conflict.get()).as("All others should get 409").isEqualTo(threads - 1);

        AvailabilityResponse avail = getAvailability(eventId);
        assertThat(avail.availableCount()).isZero();
    }

    @Test
    void twentyConcurrentReserves_onEventWithCapacity_allSucceed() throws InterruptedException {
        int threads = 20;
        UUID eventId = createEvent(threads); // 20 different seats

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);
        AtomicInteger success  = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 1; i <= threads; i++) {
            final String seatNumber = String.valueOf(i);
            final UUID userId = UUID.randomUUID();
            pool.submit(() -> {
                try {
                    start.await();
                    reserveSeat(eventId, seatNumber, userId, UUID.randomUUID().toString());
                    success.incrementAndGet();
                } catch (HttpClientErrorException.Conflict e) {
                    conflict.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(threads);
        assertThat(conflict.get()).isZero();
    }

    private UUID createEventWithOneSeat() {
        return createEvent(1);
    }

    private UUID createEvent(int seats) {
        var req = new CreateEventRequest("Concert-" + UUID.randomUUID(), "CONCERT", seats);
        var resp = restTemplate().postForObject("/api/events", req, EventResponse.class);
        assertThat(resp).isNotNull();
        return resp.id();
    }

    private ReservationResponse reserveSeat(UUID eventId, String seatNumber, UUID userId, String idemKey) {
        var req = new ReserveRequest(eventId, seatNumber, userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Idempotency-Key", idemKey);
        var entity = new HttpEntity<>(req, headers);
        return restTemplate().postForObject("/api/reservations/reserve", entity, ReservationResponse.class);
    }

    private AvailabilityResponse getAvailability(UUID eventId) {
        return restTemplate().getForObject(
                "/api/events/" + eventId + "/availability", AvailabilityResponse.class);
    }
}
