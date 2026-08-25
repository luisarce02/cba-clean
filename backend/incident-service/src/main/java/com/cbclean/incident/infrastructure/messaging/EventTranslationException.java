package com.cbclean.incident.infrastructure.messaging;

/**
 * Thrown when an inbound {@code ReportCreatedEvent} carries a report type or
 * priority that has no counterpart in the Incident Service domain enums.
 *
 * <p>Thrown from the messaging infrastructure only. Classified as a permanent
 * (poison message) failure: redelivery can never succeed, so the consumer
 * routes such messages straight to the dead letter queue via
 * {@link ReportCreatedEventRetryRouter#deadLetter} instead of retrying.</p>
 */
public class EventTranslationException extends RuntimeException {

    public EventTranslationException(String message) {
        super(message);
    }
}
