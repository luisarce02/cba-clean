package com.cbclean.report.infrastructure.persistence.outbox;

import com.cbclean.report.application.outbox.OutboxEntry;
import com.cbclean.report.application.port.OutboxStore;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.time.Clock;

/**
 * PostgreSQL adapter for the {@link OutboxStore} application port.
 *
 * <p>Serializes the integration event payload to JSON (the exact wire format
 * later sent to RabbitMQ) and persists the pending entry. The method carries
 * no {@code @Transactional} of its own: it is always invoked inside the
 * caller's {@code UnitOfWork}, so the outbox entry commits in the same
 * transaction as the aggregate that produced it.</p>
 */
@Repository
public class PostgresOutboxStore implements OutboxStore {

    private final OutboxJpaRepository jpa;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PostgresOutboxStore(OutboxJpaRepository jpa, ObjectMapper objectMapper, Clock clock) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void append(OutboxEntry entry) {
        jpa.save(toEntity(entry));
    }

    private OutboxEventEntity toEntity(OutboxEntry entry) {
        return new OutboxEventEntity(
                entry.eventId(),
                entry.aggregateType(),
                entry.aggregateId(),
                entry.eventType(),
                serialize(entry.payload()),
                entry.correlationId(),
                entry.occurredAt(),
                clock.instant());
    }

    private String serialize(ReportCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize outbox payload for event " + event.eventId(), e);
        }
    }
}
