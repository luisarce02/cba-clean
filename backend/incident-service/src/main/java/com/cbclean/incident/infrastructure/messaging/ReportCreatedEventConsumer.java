package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code ReportCreatedEvent} messages from the
 * {@code incident-service.report-created} queue and opens the corresponding
 * incident through {@link OpenIncidentUseCase}.
 *
 * <p>Deliberately thin. Responsibilities are limited to receiving the message,
 * letting the configured JSON converter deserialize it into the local
 * {@link ReportCreatedEvent} contract, translating it into an
 * {@link OpenIncidentCommand} via {@link ReportCreatedEventMapper}, and
 * delegating to the use case. All business validation lives in the domain; no
 * persistence is accessed directly here.</p>
 *
 * <h2>Acknowledgement semantics</h2>
 *
 * <p>The listener container runs in Spring AMQP's default {@code AUTO}
 * acknowledge mode:</p>
 *
 * <ul>
 *   <li><b>Success</b> - when this method returns normally (the use case and
 *   the MongoDB persistence behind it completed), Spring acknowledges the
 *   message and it is removed from the queue.</li>
 *   <li><b>Failure</b> - if any exception propagates out of this method
 *   (translation failure, domain validation failure, persistence failure), the
 *   message is <em>not</em> acknowledged; RabbitMQ redelivers it. Exceptions
 *   are never swallowed, but they may be logged by the container error
 *   handler.</li>
 * </ul>
 *
 * <h2>Idempotency limitation</h2>
 *
 * <p>RabbitMQ offers at-least-once delivery, so a redelivered event currently
 * opens a second incident for the same report. The event's {@code eventId}
 * (not {@code reportId}, which identifies the report rather than the event) is
 * the natural key for deduplication; a minimal idempotency mechanism built on
 * it is an explicit follow-up concern and intentionally not part of this
 * change.</p>
 */
@Component
public class ReportCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportCreatedEventConsumer.class);

    private final OpenIncidentUseCase openIncident;

    public ReportCreatedEventConsumer(OpenIncidentUseCase openIncident) {
        this.openIncident = openIncident;
    }

    @RabbitListener(queues = MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE)
    public void onReportCreated(ReportCreatedEvent event) {
        log.debug("Received ReportCreatedEvent [{}] for report [{}]",
                event.eventId(), event.reportId());
        OpenIncidentCommand command = ReportCreatedEventMapper.toCommand(event);
        openIncident.execute(command);
        log.info("Opened incident from ReportCreatedEvent [{}] for report [{}]",
                event.eventId(), event.reportId());
    }
}
