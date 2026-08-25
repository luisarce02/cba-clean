package com.cbclean.incident.infrastructure.messaging;

/**
 * Thrown when an inbound {@code ReportCreatedEvent} carries a report type or
 * priority that has no counterpart in the Incident Service domain enums.
 *
 * <p>Thrown from the messaging infrastructure only: the message is not
 * acknowledged and RabbitMQ redelivers it. This is deliberate - unknown
 * integration values must never be silently coerced into arbitrary domain
 * values. Dead-lettering of such poison messages is an explicit follow-up.</p>
 */
public class EventTranslationException extends RuntimeException {

    public EventTranslationException(String message) {
        super(message);
    }
}
