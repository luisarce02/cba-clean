package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.application.port.ProcessedEventRecorder;
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
 * {@link OpenIncidentCommand} via {@link ReportCreatedEventMapper}, claiming
 * the event for idempotent processing via the
 * {@link ProcessedEventRecorder} port, and delegating to the use case.
 * All business validation lives in the domain; no persistence is accessed
 * directly here.</p>
 *
 * <h2>Idempotency</h2>
 *
 * <p>RabbitMQ delivers at-least-once, so identical events can arrive twice -
 * sequentially or concurrently. Before invoking the use case, the consumer
 * atomically claims the event's {@code eventId} (never the {@code reportId},
 * which identifies the report, not this event occurrence) through the
 * {@link ProcessedEventRecorder} port, whose MongoDB adapter is protected by
 * a unique constraint. Only the first delivery wins the claim and creates an
 * incident; duplicate deliveries are logged and acknowledged without invoking
 * the use case.</p>
 *
 * <p>Translation happens before claiming: an unmappable (poison) message fails
 * loudly and keeps being redelivered instead of being silently swallowed as a
 * "duplicate".</p>
 *
 * <h2>Acknowledgement semantics</h2>
 *
 * <p>The listener container runs in Spring AMQP's default {@code AUTO}
 * acknowledge mode:</p>
 *
 * <ul>
 *   <li><b>Success</b> - when this method returns normally (claim won or
 *   duplicate skipped), Spring acknowledges the message.</li>
 *   <li><b>Failure</b> - if any exception propagates out of this method
 *   (translation failure, domain validation failure, persistence failure on
 *   first-time processing), the message is <em>not</em> acknowledged and
 *   RabbitMQ redelivers it. Exceptions are never swallowed, but they may be
 *   logged by the container error handler.</li>
 * </ul>
 *
 * <h2>Failure windows</h2>
 *
 * <p>This is at-least-once messaging with idempotent processing - not a
 * distributed transaction and not exactly-once delivery:</p>
 *
 * <ul>
 *   <li>If the service crashes after the event was claimed but before the
 *   incident was persisted, the redelivered message is skipped as a claimed
 *   event and no incident is created for it. The window between claim and
 *   incident persistence is accepted; eliminating it would require a
 *   transactional store shared by both writes or an outbox pattern.</li>
 *   <li>A crash after incident persistence but before acknowledgement is
 *   harmless: the redelivered message is skipped as already claimed.</li>
 * </ul>
 */
@Component
public class ReportCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportCreatedEventConsumer.class);

    static final String REPORT_CREATED_EVENT_TYPE = "report.created";

    private final OpenIncidentUseCase openIncident;
    private final ProcessedEventRecorder processedEvents;

    public ReportCreatedEventConsumer(OpenIncidentUseCase openIncident,
                                      ProcessedEventRecorder processedEvents) {
        this.openIncident = openIncident;
        this.processedEvents = processedEvents;
    }

    @RabbitListener(queues = MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE)
    public void onReportCreated(ReportCreatedEvent event) {
        log.debug("Received ReportCreatedEvent [{}] for report [{}]",
                event.eventId(), event.reportId());
        OpenIncidentCommand command = ReportCreatedEventMapper.toCommand(event);
        if (!processedEvents.tryClaim(event.eventId(), REPORT_CREATED_EVENT_TYPE)) {
            log.info("Ignored duplicate ReportCreatedEvent [{}] for report [{}]",
                    event.eventId(), event.reportId());
            return;
        }
        openIncident.execute(command);
        log.info("Opened incident from ReportCreatedEvent [{}] for report [{}]",
                event.eventId(), event.reportId());
    }
}
