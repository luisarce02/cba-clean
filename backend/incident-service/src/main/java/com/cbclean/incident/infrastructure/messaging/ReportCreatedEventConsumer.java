package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.application.port.ProcessedEventRecorder;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
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
 * loudly instead of being silently swallowed as a "duplicate".</p>
 *
 * <h2>Retry / dead-letter handling</h2>
 *
 * <p>The consumer classifies its own failures and routes them explicitly via
 * {@link ReportCreatedEventRetryRouter}; exceptions are never swallowed just to
 * make a message disappear:</p>
 *
 * <ul>
 *   <li><b>Success</b> - use case returns normally (claim won or duplicate
 *   skipped), Spring acknowledges the message.</li>
 *   <li><b>Poison message</b> - unknown report type or priority
 *   ({@link EventTranslationException}) can never succeed, so the message is
 *   routed straight to the DLQ.</li>
 *   <li><b>Transient failure</b> - any other exception (e.g. MongoDB
 *   unavailable): the message is republished onto the bounded TTL retry chain
 *   and acknowledged only once that copy is safely published; if publication
 *   itself fails, the exception propagates and RabbitMQ redelivers the
 *   original. Once {@code max-retries} retries have been performed, the next
 *   failure routes the message to the DLQ.</li>
 * </ul>
 *
 * <p>Fatally malformed JSON never reaches this method: the listener container's
 * error handler rejects such deliveries without requeueing, and the main
 * queue's dead-letter arguments ({@code x-dead-letter-exchange =
 * cba-clean.dlx}) route them straight into the DLQ.</p>
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
 *   transactional store shared by both writes or an outbox pattern. The retry
 *   chain does not change this: a retry of an already-claimed event is skipped
 *   as a duplicate before the use case runs again - retries therefore help
 *   failures that occur up to and including the claim, not failures after it.
 *   </li>
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
    private final ReportCreatedEventRetryRouter retryRouter;

    public ReportCreatedEventConsumer(OpenIncidentUseCase openIncident,
                                      ProcessedEventRecorder processedEvents,
                                      ReportCreatedEventRetryRouter retryRouter) {
        this.openIncident = openIncident;
        this.processedEvents = processedEvents;
        this.retryRouter = retryRouter;
    }

    @RabbitListener(queues = MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE)
    public void onReportCreated(
            ReportCreatedEvent event,
            @Header(name = MessagingTopology.RETRY_COUNT_HEADER, required = false) Integer previousRetries,
            @Header(name = MessagingTopology.CORRELATION_ID_HEADER, required = false) String correlationId) {
        try {
            // The listener container reuses threads across messages: the MDC is
            // populated for this delivery only and always cleared in finally,
            // so no correlation context can leak into the next message.
            if (correlationId != null && !correlationId.isBlank()) {
                MDC.put(MessagingTopology.CORRELATION_ID_MDC_KEY, correlationId);
            }
            log.info("operation=event-receive result=accepted eventType={} eventId={} reportId={} retry={}",
                    REPORT_CREATED_EVENT_TYPE, event.eventId(), event.reportId(),
                    previousRetries == null ? 0 : previousRetries);
            try {
                process(event, correlationId);
            } catch (EventTranslationException poison) {
                retryRouter.deadLetter(event, previousRetries, poison, correlationId);
            } catch (RuntimeException transientFailure) {
                log.error("operation=event-process result=failed eventType={} eventId={} reportId={}",
                        REPORT_CREATED_EVENT_TYPE, event.eventId(), event.reportId(), transientFailure);
                retryRouter.retryOrDeadLetter(event, previousRetries, transientFailure, correlationId);
            }
        } finally {
            MDC.clear();
        }
    }

    private void process(ReportCreatedEvent event, String correlationId) {
        OpenIncidentCommand command = ReportCreatedEventMapper.toCommand(event);
        log.info("operation=event-map result=mapped eventType={} eventId={} reportId={}",
                REPORT_CREATED_EVENT_TYPE, event.eventId(), event.reportId());
        if (!processedEvents.tryClaim(event.eventId(), REPORT_CREATED_EVENT_TYPE)) {
            log.info("operation=event-deduplicate result=duplicate-ignored eventType={} eventId={} reportId={}",
                    REPORT_CREATED_EVENT_TYPE, event.eventId(), event.reportId());
            return;
        }
        Incident incident = openIncident.execute(command);
        log.info("operation=incident-create result=created eventType={} eventId={} reportId={} incidentId={} "
                        + "correlationId={}",
                REPORT_CREATED_EVENT_TYPE, event.eventId(), event.reportId(),
                incident.id().value(), correlationId);
    }
}
