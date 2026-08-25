package com.cbclean.report.application.port;

import com.cbclean.report.integration.event.ReportCreatedEvent;

/**
 * Application port for publishing integration events about reports.
 *
 * <p>Owned by the application layer and expressed in terms of the
 * {@link ReportCreatedEvent} integration contract, so that use cases never
 * depend on a concrete messaging technology such as RabbitMQ.</p>
 *
 * <p><strong>Failure semantics:</strong> publication is a best-effort,
 * synchronous side effect that happens only after the report has been
 * successfully persisted. Persistence and publication are deliberately
 * <em>not</em> atomic: if persistence succeeds but publication fails, the
 * report remains stored and the failure surfaces to the caller (no distributed
 * transaction, no outbox yet).</p>
 */
public interface ReportEventPublisher {

    /**
     * Publishes a {@link ReportCreatedEvent} to the messaging infrastructure.
     *
     * @param event the event to publish; must be fully populated
     */
    void publishReportCreated(ReportCreatedEvent event);
}
