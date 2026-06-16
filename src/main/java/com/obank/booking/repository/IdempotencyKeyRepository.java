package com.obank.booking.repository;

import com.obank.booking.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKey.IdempotencyKeyPK> {

    Optional<IdempotencyKey> findByIdempotencyKeyAndOperation(String idempotencyKey, String operation);
}
