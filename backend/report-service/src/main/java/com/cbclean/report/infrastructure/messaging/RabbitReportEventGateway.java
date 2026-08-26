package com.cbclean.report.infrastructure.messaging;

import com.cbclean.report.config.OutboxProperties;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Low-level RabbitMQ gateway for {@link ReportCreatedEvent} publications.
 *
 * <p>Owns the single wire contract of the Report Service: exchange, routing
 * key, JSON serialization (via the RabbitTemplate's Jackson converter),
 * persistent delivery mode and the {@code eventType}/{@code eventId}/
 * {@code correlationId} headers. Both the outbox publisher and the
 * direct-publish adapter ({@link RabbitReportEventPublisher}) go through it,
 * so there is exactly one place defining how an event looks on the wire.</p>
 *
 * <p><strong>Publisher confirms:</strong> the method blocks until the broker's
 * confirm for this message arrives (requires
 * {@code spring.rabbitmq.publisher-confirm-type=correlated}). It returns only
 * after RabbitMQ accepted the message; otherwise it throws
 * {@link EventPublicationException}. Callers must treat "returned without
 * exception" as "broker accepted", never as merely "not thrown yet".</p>
 */
@Component
public class RabbitReportEventGateway {

    private static final Logger log = LoggerFactory.getLogger(RabbitReportEventGateway.class);

    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String REPORT_CREATED_EVENT_TYPE = "report.created";
    public static final String EVENT_ID_HEADER = "eventId";
    public static final String CORRELATION_ID_HEADER = "correlationId";

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMillis;

    public RabbitReportEventGateway(RabbitTemplate rabbitTemplate, OutboxProperties properties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "RabbitTemplate is required");
        this.confirmTimeoutMillis = properties.publishConfirmTimeout().toMillis();
    }

    /**
     * Publishes the event and waits for the broker's confirmation.
     *
     * @param event         the integration event to publish
     * @param correlationId explicit correlation ID carried in the message
     *                      header; may be {@code null} to omit the header
     * @throws EventPublicationException when the broker is unreachable,
     *                                   rejects (nacks) the message or does not
     *                                   confirm within the configured timeout
     */
    public void publish(ReportCreatedEvent event, String correlationId) {
        CorrelationData confirmation = new CorrelationData(event.eventId().toString());
        try {
            rabbitTemplate.convertAndSend(
                    MessagingTopology.EVENTS_EXCHANGE,
                    MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                    event,
                    metadata(event, correlationId),
                    confirmation);
        } catch (AmqpException e) {
            throw new EventPublicationException(
                    "RabbitMQ rejected publication of event " + event.eventId(), e);
        }

        try {
            CorrelationData.Confirm confirm =
                    confirmation.getFuture().get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "no confirm received" : confirm.getReason();
                throw new EventPublicationException(
                        "RabbitMQ did not accept event " + event.eventId() + ": " + reason);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublicationException(
                    "Interrupted while waiting for publish confirm of event " + event.eventId(), e);
        } catch (TimeoutException e) {
            throw new EventPublicationException(
                    "Timed out waiting for publish confirm of event " + event.eventId(), e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new EventPublicationException(
                    "Publish confirm for event " + event.eventId() + " failed", e);
        }

        log.info("operation=event-publish result=confirmed eventId={} reportId={}",
                event.eventId(), event.reportId());
    }

    private MessagePostProcessor metadata(ReportCreatedEvent event, String correlationId) {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setHeader(EVENT_TYPE_HEADER, REPORT_CREATED_EVENT_TYPE);
            properties.setHeader(EVENT_ID_HEADER, event.eventId().toString());
            if (correlationId != null && !correlationId.isBlank()) {
                properties.setHeader(CORRELATION_ID_HEADER, correlationId.trim());
            }
            return message;
        };
    }
}
