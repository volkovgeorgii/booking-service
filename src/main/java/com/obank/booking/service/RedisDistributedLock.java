package com.obank.booking.service;

import com.obank.booking.exception.InvalidStateException;
import com.obank.booking.exception.SeatUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private static final String CB_NAME = "redis-lock";

    private static final DefaultRedisScript<String> ACQUIRE_SCRIPT =
            new DefaultRedisScript<>(
                    "return redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])",
                    String.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    else
                        return 0
                    end
                    """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    public Optional<String> tryAcquire(String key, long ttlMs) {
        String token = UUID.randomUUID().toString();
        String result = redisTemplate.execute(ACQUIRE_SCRIPT,
                List.of(key), token, String.valueOf(ttlMs));
        return "OK".equals(result) ? Optional.of(token) : Optional.empty();
    }

    public void release(String key, String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
        } catch (Exception e) {
            log.warn("Failed to release Redis lock '{}': {}", key, e.getMessage());
        }
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "withLockFallback")
    public <T> T withLock(String key, long ttlMs, Supplier<T> action) {
        String token = tryAcquire(key, ttlMs)
                .orElseThrow(() -> new SeatUnavailableException(
                        "Seat is currently being processed by another request"));
        try {
            return action.get();
        } finally {
            release(key, token);
        }
    }

    @SuppressWarnings("unused")
    public <T> T withLockFallback(String key, long ttlMs, Supplier<T> action, Exception cause) {
        if (cause instanceof SeatUnavailableException e) throw e;
        if (cause instanceof InvalidStateException e) throw e;

        log.warn("Redis circuit open — executing '{}' without distributed lock. " +
                "Falling back to DB-level locking only. Cause: {}", key, cause.getMessage());
        return action.get();
    }
}
