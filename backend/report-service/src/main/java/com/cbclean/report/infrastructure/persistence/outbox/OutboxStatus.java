package com.cbclean.report.infrastructure.persistence.outbox;

/**
 * Publication lifecycle of an outbox event.
 *
 * <ul>
 *   <li>{@code PENDING} - committed with its aggregate, awaiting publication.</li>
 *   <li>{@code PUBLISHING} - atomically claimed by a publisher instance
 *   (protected by {@code FOR UPDATE SKIP LOCKED}); prevents concurrent
 *   publishers from working on the same event. If publication fails, the event
 *   returns to {@code PENDING} for the next poll.</li>
 *   <li>{@code PUBLISHED} - RabbitMQ confirmed acceptance of the message.
 *   This is the only way an event becomes published.</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED
}
