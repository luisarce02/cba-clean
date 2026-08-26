package com.cbclean.report.application.outbox;

import com.cbclean.report.integration.event.ReportCreatedEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Application-level representation of a pending integration event destined
 * for the transactional outbox.
 *
 * <p>Carries everything required to publish the event later without touching
 * the originating aggregate again: the wire-format payload itself plus
 * transport/observability metadata (event type and correlation ID). The
 * {@code eventId} of the embedded {@link ReportCreatedEvent} is the single
 * identity of this occurrence - it becomes the outbox primary key and the
 * {@code eventId} RabbitMQ header, which is what Incident Service idempotency
 * deduplicates on.</p>
 *
 * <p>This record is persistence- and broker-agnostic; serialization to JSON
 * and any status bookkeeping belong to the infrastructure adapters.</p>
 */
public record OutboxEntry(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        String correlationId,
        ReportCreatedEvent payload) {

    public static final String REPORT_CREATED_EVENT_TYPE = "report.created";
    private static final String REPORT_AGGREGATE_TYPE = "report";

    public OutboxEntry {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(eventType, "eventType is required");
        Objects.requireNonNull(aggregateType, "aggregateType is required");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(payload, "payload is required");
        correlationId = normalize(correlationId);
    }

    /**
     * Creates the outbox entry for a {@link ReportCreatedEvent}. The event ID
     * of the integration event is reused as the outbox identity so downstream
     * consumers keep seeing one stable event ID per business occurrence.
     */
    public static OutboxEntry forReportCreated(ReportCreatedEvent event, String correlationId) {
        return new OutboxEntry(
                event.eventId(),
                REPORT_CREATED_EVENT_TYPE,
                REPORT_AGGREGATE_TYPE,
                event.reportId(),
                event.occurredAt(),
                correlationId,
                event);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
