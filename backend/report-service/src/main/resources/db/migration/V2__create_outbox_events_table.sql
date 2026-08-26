CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    payload         TEXT         NOT NULL,
    correlation_id  VARCHAR(128),
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER      NOT NULL DEFAULT 0,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    last_error      VARCHAR(1024),
    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED'))
);

-- Serves the polling query exactly:
-- SELECT ... WHERE status = 'PENDING' ORDER BY created_at LIMIT n FOR UPDATE SKIP LOCKED
CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at) WHERE status = 'PENDING';
