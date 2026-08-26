package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.infrastructure.metrics.IncidentMetrics;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Routes failed {@code ReportCreatedEvent} deliveries through the bounded
 * retry chain or, when retries are exhausted (or the failure is classified as
 * permanent), into the DLQ.
 *
 * <p>Routing is consumer-managed: on failure the current delivery is
 * acknowledged after the message has been safely republished to the
 * appropriate infrastructure queue - it is never silently dropped.</p>
 *
 * <ul>
 *   <li><b>Transient failure</b> (e.g. MongoDB unavailable): republished to
 *   {@code cba-clean.dlx} with routing key
 *   {@code incident-service.report-created.retry.N}, carrying an incremented
 *   {@value MessagingTopology#RETRY_COUNT_HEADER} header. Retry queue N waits
 *   its configured TTL and dead-letters the message back onto the main queue.
 *   When the counter reaches {@code max-retries}, the message goes to the DLQ.</li>
 *   <li><b>Permanent / poison message</b> ({@link EventTranslationException},
 *   i.e. unknown report type or priority): routed straight to the DLQ -
 *   redelivery cannot succeed, so no time is spent on retries.</li>
 * </ul>
 */
@Component
public class ReportCreatedEventRetryRouter {

    private static final Logger log = LoggerFactory.getLogger(ReportCreatedEventRetryRouter.class);

    private final RabbitTemplate rabbitTemplate;
    private final IncidentMessagingRetryProperties retryProperties;
    private final IncidentMetrics metrics;

    public ReportCreatedEventRetryRouter(RabbitTemplate rabbitTemplate,
                                         IncidentMessagingRetryProperties retryProperties,
                                         IncidentMetrics metrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.retryProperties = retryProperties;
        this.metrics = metrics;
    }

    /**
     * Handles a transient processing failure: schedules the next retry while
     * any remain, otherwise dead-letters the message.
     */
    public void retryOrDeadLetter(ReportCreatedEvent event,
                                  Integer previousRetries,
                                  RuntimeException cause,
                                  String correlationId) {
        int retriesSoFar = previousRetries == null ? 0 : previousRetries;
        if (retriesSoFar >= retryProperties.getMaxRetries()) {
            log.error("operation=event-route result=dead-lettered reason=exhausted eventType={} eventId={} "
                            + "reportId={} retries={}",
                    "report.created", event.eventId(), event.reportId(), retriesSoFar, cause);
            routeToDeadLetter(event, retriesSoFar, cause, correlationId,
                    IncidentMetrics.REASON_RETRY_EXHAUSTED);
            return;
        }
        int nextRetry = retriesSoFar + 1;
        String routingKey = MessagingTopology.retryQueue(nextRetry);
        long delayMillis = retryProperties.getDelays().get(retriesSoFar).toMillis();
        metrics.retryScheduled("report.created");
        log.warn("operation=event-retry result=scheduled eventType={} eventId={} reportId={} attempt={} of {} in {}ms",
                "report.created", event.eventId(), event.reportId(), nextRetry,
                retryProperties.getMaxRetries(), delayMillis);
        rabbitTemplate.convertAndSend(
                MessagingTopology.DEAD_LETTER_EXCHANGE,
                routingKey,
                event,
                message -> {
                    message.getMessageProperties().setHeader(MessagingTopology.RETRY_COUNT_HEADER, nextRetry);
                    stampCorrelationId(message, correlationId);
                    return message;
                });
    }

    /**
     * Routes a permanently unprocessable (poison) message straight to the DLQ.
     */
    public void deadLetter(ReportCreatedEvent event,
                           Integer previousRetries,
                           RuntimeException cause,
                           String correlationId) {
        routeToDeadLetter(event, previousRetries == null ? 0 : previousRetries, cause, correlationId,
                IncidentMetrics.REASON_TRANSLATION_FAILURE);
    }

    private void routeToDeadLetter(ReportCreatedEvent event,
                                   int retriesSoFar,
                                   RuntimeException cause,
                                   String correlationId,
                                   String reason) {
        log.error("operation=event-route result=dead-lettered reason={} eventType={} eventId={} reportId={} "
                        + "retries={}",
                reason, "report.created", event.eventId(), event.reportId(), retriesSoFar, cause);
        metrics.deadLettered("report.created", reason);
        rabbitTemplate.convertAndSend(
                MessagingTopology.DEAD_LETTER_EXCHANGE,
                MessagingTopology.INCIDENT_REPORT_CREATED_DLQ,
                event,
                message -> {
                    message.getMessageProperties()
                            .setHeader(MessagingTopology.RETRY_COUNT_HEADER, retriesSoFar);
                    stampCorrelationId(message, correlationId);
                    return message;
                });
    }

    private static void stampCorrelationId(org.springframework.amqp.core.Message message,
                                           String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            message.getMessageProperties()
                    .setHeader(MessagingTopology.CORRELATION_ID_HEADER, correlationId);
        }
    }
}
