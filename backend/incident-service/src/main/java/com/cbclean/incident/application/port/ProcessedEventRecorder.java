package com.cbclean.incident.application.port;

import java.util.UUID;

/**
 * Port for idempotent event processing.
 *
 * <p>RabbitMQ delivers messages at-least-once, so the same event may be
 * handed to a listener more than once, and identical events may be delivered
 * concurrently. Implementations must decide atomically - typically backed by
 * a unique constraint on {@code eventId} in durable storage - whether a caller
 * is the first handler of an event.</p>
 *
 * <p>The event's {@code eventId} is the identity used here. It identifies one
 * occurrence of an integration event; it is deliberately distinct from
 * {@code reportId}, which identifies the business entity (the report) the
 * event talks about.</p>
 */
public interface ProcessedEventRecorder {

    /**
     * Atomically claims the event identified by {@code eventId}.
     *
     * @param eventId   identity of the integration event (never the report id)
     * @param eventType logical type of the event, e.g. {@code report.created}
     * @return {@code true} if this caller claimed the event and should process
     *         it; {@code false} if the event was already claimed or processed
     *         by another delivery and must be skipped
     */
    boolean tryClaim(UUID eventId, String eventType);
}
