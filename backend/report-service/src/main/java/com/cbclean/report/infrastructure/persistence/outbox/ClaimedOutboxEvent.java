package com.cbclean.report.infrastructure.persistence.outbox;

import java.util.UUID;

/**
 * Immutable snapshot of an outbox event claimed for publication. Contains
 * everything required to publish without re-reading the aggregate.
 */
public record ClaimedOutboxEvent(
        UUID id,
        String eventType,
        String payload,
        String correlationId,
        int attempts) {

    static ClaimedOutboxEvent from(OutboxEventEntity entity) {
        return new ClaimedOutboxEvent(
                entity.getId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getCorrelationId(),
                entity.getAttempts());
    }
}
