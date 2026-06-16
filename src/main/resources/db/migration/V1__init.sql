CREATE TABLE events (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    total_seats INT          NOT NULL CHECK (total_seats > 0),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE seats (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID        NOT NULL REFERENCES events(id),
    seat_number  VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'FREE'
                              CHECK (status IN ('FREE', 'RESERVED', 'SOLD')),
    reserved_by  UUID,
    reserved_until TIMESTAMPTZ,
    version      INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_seat UNIQUE (event_id, seat_number),
    CONSTRAINT reserved_consistency CHECK (
        (status = 'RESERVED' AND reserved_until IS NOT NULL)
        OR (status <> 'RESERVED')
    )
);

CREATE TABLE reservations (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id       UUID        NOT NULL REFERENCES events(id),
    seat_id        UUID        NOT NULL REFERENCES seats(id),
    seat_number    VARCHAR(20)  NOT NULL,
    user_id        UUID        NOT NULL,
    status         VARCHAR(20)  NOT NULL
                                CHECK (status IN ('RESERVED', 'PAID', 'CANCELLED', 'EXPIRED')),
    reserved_until TIMESTAMPTZ,
    paid_at        TIMESTAMPTZ,
    cancelled_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) NOT NULL,
    operation       VARCHAR(50)  NOT NULL,
    reservation_id  UUID,
    http_status     INT          NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_idempotency PRIMARY KEY (idempotency_key, operation)
);

CREATE INDEX idx_seats_event_status ON seats (event_id, status);
CREATE INDEX idx_reservations_reserved_until ON reservations (reserved_until)
    WHERE status = 'RESERVED';
CREATE INDEX idx_reservations_user_id ON reservations (user_id, created_at DESC);
CREATE INDEX idx_idempotency_created_at ON idempotency_keys (created_at);
