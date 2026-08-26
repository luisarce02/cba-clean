package com.cbclean.report.infrastructure.persistence.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence model of the transactional outbox (table {@code outbox_events},
 * created by {@code V2__create_outbox_events_table.sql}).
 *
 * <p>The primary key is the integration event's {@code eventId}, so one
 * business event occurrence exists exactly once and downstream idempotency can
 * rely on that identity end to end.</p>
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;

    private String aggregateType;

    private UUID aggregateId;

    private String eventType;

    private String payload;

    private String correlationId;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    private int attempts;

    private Instant occurredAt;

    private Instant createdAt;

    private Instant lastAttemptAt;

    private Instant publishedAt;

    private String lastError;

    protected OutboxEventEntity() {
        // Required by JPA.
    }

    public OutboxEventEntity(UUID id, String aggregateType, UUID aggregateId, String eventType,
                             String payload, String correlationId, Instant occurredAt, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType is required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId is required");
        this.eventType = Objects.requireNonNull(eventType, "eventType is required");
        this.payload = Objects.requireNonNull(payload, "payload is required");
        this.correlationId = correlationId;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt is required");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    /**
     * Atomically claims this event for publication (PENDING &rarr; PUBLISHING).
     */
    void markPublishing(Instant attemptAt) {
        this.status = OutboxStatus.PUBLISHING;
        this.attempts++;
        this.lastAttemptAt = attemptAt;
    }

    /**
     * Confirms successful publication (PUBLISHING &rarr; PUBLISHED). Only ever
     * called after RabbitMQ accepted the message.
     */
    void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    /**
     * Returns a failed publication attempt to the pollable state
     * (PUBLISHING &rarr; PENDING), keeping bounded retry metadata.
     */
    void markPublishingFailed(String error) {
        this.status = OutboxStatus.PENDING;
        this.lastError = error == null ? "unknown error" : truncate(error);
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getLastError() {
        return lastError;
    }

    private static String truncate(String value) {
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }
}
