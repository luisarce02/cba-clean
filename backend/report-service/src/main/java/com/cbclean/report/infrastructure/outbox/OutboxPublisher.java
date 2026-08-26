package com.cbclean.report.infrastructure.outbox;

import com.cbclean.report.application.outbox.OutboxEntry;
import com.cbclean.report.application.correlation.CorrelationContext;
import com.cbclean.report.config.OutboxProperties;
import com.cbclean.report.infrastructure.messaging.EventPublicationException;
import com.cbclean.report.infrastructure.messaging.RabbitReportEventGateway;
import com.cbclean.report.infrastructure.persistence.outbox.ClaimedOutboxEvent;
import com.cbclean.report.infrastructure.persistence.outbox.OutboxEventRepository;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Asynchronous publisher that drains the transactional outbox into RabbitMQ.
 *
 * <p>Each polling round:</p>
 * <ol>
 *   <li>claims a batch of {@code PENDING} events atomically
 *   ({@code FOR UPDATE SKIP LOCKED}),</li>
 *   <li>publishes each one through the shared RabbitMQ gateway and waits for
 *   the broker's publisher confirm,</li>
 *   <li>marks it {@code PUBLISHED} only after confirmation - failed attempts
 *   return to {@code PENDING} and are retried on a later round.</li>
 * </ol>
 *
 * <p><strong>Delivery semantics: at-least-once.</strong> A crash between broker
 * acceptance and marking the row published can re-publish an event; Incident
 * Service idempotency (deduplication on {@code eventId}) makes this safe.
 * Exactly-once delivery is explicitly not provided.</p>
 *
 * <p>This retry loop covers "RabbitMQ was unavailable while publishing". It is
 * deliberately separate from the consumer-side retry/DLQ mechanism, which
 * handles "the event was received but processing failed".</p>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;
    private final RabbitReportEventGateway gateway;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxPublisher(OutboxEventRepository outboxEvents, ObjectMapper objectMapper,
                           RabbitReportEventGateway gateway, OutboxProperties properties,
                           OutboxMetrics metrics, Clock clock) {
        this.outboxEvents = outboxEvents;
        this.objectMapper = objectMapper;
        this.gateway = gateway;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${cbaclean.outbox.poll-interval:PT5S}")
    public void publishPendingBatch() {
        List<ClaimedOutboxEvent> batch = outboxEvents.claimPending(properties.batchSize());
        if (!batch.isEmpty()) {
            log.info("operation=outbox-publish result=batch-claimed size={}", batch.size());
        }
        for (ClaimedOutboxEvent candidate : batch) {
            publishOne(candidate);
        }
        metrics.recordPending(outboxEvents.countPending());
    }

    private void publishOne(ClaimedOutboxEvent candidate) {
        log.info("operation=outbox-publish result=attempted eventId={} eventType={} attempts={}",
                candidate.id(), candidate.eventType(), candidate.attempts());
        try {
            ReportCreatedEvent event = deserialize(candidate);
            requireMatchingIdentity(candidate, event);
            publishWithCorrelationLogging(candidate, event);
            outboxEvents.markPublished(candidate.id(), clock.instant());
            metrics.eventPublished(OutboxEntry.REPORT_CREATED_EVENT_TYPE);
            log.info("operation=outbox-publish result=published eventId={}", candidate.id());
        } catch (RuntimeException failure) {
            outboxEvents.markPublishingFailed(candidate.id(), failure.getMessage());
            metrics.eventPublishFailed(OutboxEntry.REPORT_CREATED_EVENT_TYPE);
            log.error("operation=outbox-publish result=failed eventId={} retryScheduled=true",
                    candidate.id(), failure);
        }
    }

    private void publishWithCorrelationLogging(ClaimedOutboxEvent candidate, ReportCreatedEvent event) {
        String correlationId = candidate.correlationId();
        if (correlationId != null) {
            MDC.put(CorrelationContext.MDC_KEY, correlationId);
        }
        try {
            gateway.publish(event, correlationId);
        } finally {
            if (correlationId != null) {
                MDC.remove(CorrelationContext.MDC_KEY);
            }
        }
    }

    private ReportCreatedEvent deserialize(ClaimedOutboxEvent candidate) {
        try {
            return objectMapper.readValue(candidate.payload(), ReportCreatedEvent.class);
        } catch (IOException | RuntimeException e) {
            throw new EventPublicationException(
                    "Failed to deserialize outbox payload of event " + candidate.id(), e);
        }
    }

    /**
     * Guards the invariant outbox id == wire event id; a mismatch means a
     * corrupt row and must not be published under the wrong identity.
     */
    private void requireMatchingIdentity(ClaimedOutboxEvent candidate, ReportCreatedEvent event) {
        if (!candidate.id().equals(event.eventId())) {
            throw new EventPublicationException("Outbox id " + candidate.id()
                    + " does not match payload eventId " + event.eventId());
        }
    }
}
