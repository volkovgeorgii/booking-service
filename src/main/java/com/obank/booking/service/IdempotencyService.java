package com.obank.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obank.booking.domain.IdempotencyKey;
import com.obank.booking.repository.IdempotencyKeyRepository;
import com.obank.booking.web.dto.ReservationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String REDIS_PREFIX = "idempotency:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public Optional<ReservationResponse> findCached(String key, String operation) {
        String redisKey = REDIS_PREFIX + operation + ":" + key;
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return Optional.of(deserialize(cached));
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ReservationResponse> findInDb(String key, String operation) {
        return repository.findByIdempotencyKeyAndOperation(key, operation)
                .map(ik -> deserialize(ik.getResponseBody()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String key, String operation, UUID reservationId,
                     int httpStatus, ReservationResponse response) {
        String serialized = serialize(response);
        if (repository.findByIdempotencyKeyAndOperation(key, operation).isEmpty()) {
            repository.save(new IdempotencyKey(key, operation, reservationId,
                    httpStatus, serialized));
        }
        try {
            redisTemplate.opsForValue().set(REDIS_PREFIX + operation + ":" + key, serialized, CACHE_TTL);
        } catch (Exception e) {
            // Redis недоступен, следующий запрос возьмёт из БД
        }
    }

    private String serialize(ReservationResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reservation response", e);
        }
    }

    private ReservationResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, ReservationResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize reservation response", e);
        }
    }
}
