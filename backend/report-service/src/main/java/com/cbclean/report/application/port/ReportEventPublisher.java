package com.cbclean.report.application.port;

import com.cbclean.report.integration.event.ReportCreatedEvent;

/**
 * Application port for directly publishing integration events about reports.
 *
 * <p><strong>No longer part of the report submission flow:</strong> since the
 * Transactional Outbox Pattern was introduced, {@code SubmitReportUseCase}
 * persists its events via the {@link OutboxStore} port instead, and the outbox
 * publisher delivers them asynchronously. This direct-publish port remains as
 * an explicit adapter for tooling and infrastructure tests; there is exactly
 * one production path for {@link ReportCreatedEvent}:
 * submission &rarr; PostgreSQL transaction (report + outbox) &rarr; outbox
 * publisher &rarr; RabbitMQ.</p>
 *
 * <p><strong>Failure semantics:</strong> publication is best-effort; failures
 * surface to the caller and are not retried here.</p>
 */
public interface ReportEventPublisher {

    /**
     * Publishes a {@link ReportCreatedEvent} to the messaging infrastructure.
     *
     * @param event the event to publish; must be fully populated
     */
    void publishReportCreated(ReportCreatedEvent event);
}
