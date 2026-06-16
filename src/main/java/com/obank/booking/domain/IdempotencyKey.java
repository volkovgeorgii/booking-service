package com.obank.booking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@IdClass(IdempotencyKey.IdempotencyKeyPK.class)
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Id
    @Column(name = "operation", nullable = false)
    private String operation;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IdempotencyKey(String idempotencyKey, String operation, UUID reservationId,
                          int httpStatus, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.operation = operation;
        this.reservationId = reservationId;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public record IdempotencyKeyPK(String idempotencyKey, String operation) implements Serializable {}
}
