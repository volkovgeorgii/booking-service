package com.obank.booking.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.obank.booking.domain.IdempotencyKey;
import com.obank.booking.domain.ReservationStatus;
import com.obank.booking.repository.IdempotencyKeyRepository;
import com.obank.booking.service.IdempotencyService;
import com.obank.booking.web.dto.ReservationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class IdempotencyServiceTest {

    @Mock IdempotencyKeyRepository repository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    IdempotencyService service;
    ObjectMapper objectMapper;

    private static final String KEY = "idem-key-abc";
    private static final String OP  = "RESERVE";
    private static final String REDIS_KEY = "idempotency:RESERVE:" + KEY;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new IdempotencyService(repository, objectMapper, redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void findCached_returnsResponse_onRedisHit() throws Exception {
        ReservationResponse expected = sampleResponse();
        when(valueOps.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(expected));

        Optional<ReservationResponse> result = service.findCached(KEY, OP);

        assertThat(result).isPresent();
        assertThat(result.get().reservationId()).isEqualTo(expected.reservationId());
        verify(repository, never()).findByIdempotencyKeyAndOperation(any(), any());
    }

    @Test
    void findCached_returnsEmpty_onRedisMiss_withoutQueryingDb() {
        when(valueOps.get(REDIS_KEY)).thenReturn(null);

        Optional<ReservationResponse> result = service.findCached(KEY, OP);

        assertThat(result).isEmpty();
        verify(repository, never()).findByIdempotencyKeyAndOperation(any(), any());
    }

    @Test
    void save_writesDbFirst_thenRedis() throws Exception {
        ReservationResponse response = sampleResponse();
        when(repository.findByIdempotencyKeyAndOperation(KEY, OP)).thenReturn(Optional.empty());

        service.save(KEY, OP, response.reservationId(), 200, response);

        InOrder order = inOrder(repository, valueOps);
        order.verify(repository).findByIdempotencyKeyAndOperation(KEY, OP);
        order.verify(repository).save(any(IdempotencyKey.class));
        order.verify(valueOps).set(eq(REDIS_KEY), anyString(), any());
    }

    @Test
    void save_silentlyIgnoresRedisFailure_dbRecordIsPreserved() throws Exception {
        ReservationResponse response = sampleResponse();
        when(repository.findByIdempotencyKeyAndOperation(KEY, OP)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Redis down")).when(valueOps).set(any(), any(), any());

        assertThatNoException().isThrownBy(() ->
            service.save(KEY, OP, response.reservationId(), 200, response)
        );
        verify(repository).save(any(IdempotencyKey.class));
    }

    @Test
    void save_skipsDbInsert_whenKeyAlreadyExists() throws Exception {
        ReservationResponse response = sampleResponse();
        when(repository.findByIdempotencyKeyAndOperation(KEY, OP))
                .thenReturn(Optional.of(mock(IdempotencyKey.class)));

        service.save(KEY, OP, response.reservationId(), 200, response);

        verify(repository, never()).save(any());
        verify(valueOps).set(eq(REDIS_KEY), anyString(), any());
    }

    @Test
    void findInDb_queriesRepositoryOnly() throws Exception {
        ReservationResponse expected = sampleResponse();
        IdempotencyKey entity = new IdempotencyKey(KEY, OP, expected.reservationId(), 200,
                objectMapper.writeValueAsString(expected));
        when(repository.findByIdempotencyKeyAndOperation(KEY, OP)).thenReturn(Optional.of(entity));

        Optional<ReservationResponse> result = service.findInDb(KEY, OP);

        assertThat(result).isPresent();
        assertThat(result.get().reservationId()).isEqualTo(expected.reservationId());
        verify(valueOps, never()).get(any());
    }

    private ReservationResponse sampleResponse() {
        return new ReservationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "A1",
                UUID.randomUUID(),
                ReservationStatus.RESERVED,
                Instant.now().plus(10, ChronoUnit.MINUTES),
                null,
                null,
                Instant.now()
        );
    }
}
