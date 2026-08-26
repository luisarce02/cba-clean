package com.cbclean.report.infrastructure.messaging;

/**
 * Signals that RabbitMQ did not accept a published event: the broker was
 * unreachable, the publication was nacked, or no publisher confirmation
 * arrived within the configured timeout. The outbox publisher treats this as
 * a failed attempt and leaves the event pending for the next poll.
 */
public class EventPublicationException extends RuntimeException {

    public EventPublicationException(String message) {
        super(message);
    }

    public EventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
