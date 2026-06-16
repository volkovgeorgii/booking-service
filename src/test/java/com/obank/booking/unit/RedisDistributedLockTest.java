package com.obank.booking.unit;

import com.obank.booking.exception.InvalidStateException;
import com.obank.booking.exception.SeatUnavailableException;
import com.obank.booking.service.RedisDistributedLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

    @Mock
    StringRedisTemplate redisTemplate;

    RedisDistributedLock lock;

    @BeforeEach
    void setUp() {
        lock = new RedisDistributedLock(redisTemplate);
    }

    @Test
    void tryAcquire_returnsToken_whenRedisGrantsLock() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn("OK");

        Optional<String> token = lock.tryAcquire("lock:seat:inv1:1", 5000);

        assertThat(token).isPresent();
    }

    @Test
    void tryAcquire_returnsEmpty_whenSeatAlreadyLocked() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(null);

        Optional<String> token = lock.tryAcquire("lock:seat:inv1:1", 5000);

        assertThat(token).isEmpty();
    }

    @Test
    void withLock_executesAction_andReleasesLock() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn("OK");

        AtomicBoolean executed = new AtomicBoolean(false);
        lock.withLock("lock:seat:inv1:1", 5000, () -> {
            executed.set(true);
            return "result";
        });

        assertThat(executed.get()).isTrue();
        verify(redisTemplate, atLeastOnce()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void withLock_releasesLock_evenWhenActionThrows() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn("OK");

        assertThatThrownBy(() ->
            lock.withLock("lock:seat:inv1:1", 5000, () -> {
                throw new RuntimeException("DB error");
            })
        ).isInstanceOf(RuntimeException.class);

        verify(redisTemplate, atLeastOnce()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void withLock_throwsSeatUnavailable_whenLockNotAcquired() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(null);

        assertThatThrownBy(() -> lock.withLock("lock:seat:inv1:1", 5000, () -> null))
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining("currently being processed");
    }

    @Test
    void release_silentlyIgnores_redisFailure() {
        doThrow(new RuntimeException("Redis unavailable"))
                .when(redisTemplate).execute(any(RedisScript.class), anyList(), anyString());

        assertThatNoException().isThrownBy(() -> lock.release("lock:seat:inv1:1", "token-abc"));
    }

    @Test
    void withLockFallback_rethrowsSeatUnavailableException_withoutCallingAction() {
        SeatUnavailableException cause = new SeatUnavailableException("seat already taken");
        AtomicBoolean called = new AtomicBoolean(false);

        assertThatThrownBy(() ->
            lock.withLockFallback("key", 5000, () -> { called.set(true); return null; }, cause)
        ).isSameAs(cause);

        assertThat(called.get())
                .as("action must NOT be called when cause is a business exception")
                .isFalse();
    }

    @Test
    void withLockFallback_rethrowsInvalidStateException_withoutCallingAction() {
        InvalidStateException cause = new InvalidStateException("booking already confirmed");
        AtomicBoolean called = new AtomicBoolean(false);

        assertThatThrownBy(() ->
            lock.withLockFallback("key", 5000, () -> { called.set(true); return null; }, cause)
        ).isSameAs(cause);

        assertThat(called.get())
                .as("action must NOT be called when cause is a business exception")
                .isFalse();
    }

    @Test
    void withLockFallback_executesAction_onGenuineRedisFailure() {
        RuntimeException redisDown = new RuntimeException("Redis connection refused");
        AtomicBoolean called = new AtomicBoolean(false);

        lock.withLockFallback("key", 5000, () -> { called.set(true); return null; }, redisDown);

        assertThat(called.get())
                .as("action MUST be called when cause is a Redis infrastructure failure")
                .isTrue();
    }
}
