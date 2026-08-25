package com.cbclean.report.infrastructure.messaging;

import com.cbclean.report.application.port.ReportEventPublisher;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ adapter for the {@link ReportEventPublisher} application port.
 *
 * <p>Publishes {@link ReportCreatedEvent} instances as persistent JSON
 * messages to the {@code cba-clean.events} topic exchange using the
 * {@code report.created} routing key. JSON serialization uses the Spring
 * application context's Jackson configuration via the RabbitTemplate's
 * message converter.</p>
 */
@Component
public class RabbitReportEventPublisher implements ReportEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitReportEventPublisher.class);

    static final String EVENT_TYPE_HEADER = "eventType";
    static final String REPORT_CREATED_EVENT_TYPE = "report.created";
    static final String EVENT_ID_HEADER = "eventId";

    private final RabbitTemplate rabbitTemplate;

    public RabbitReportEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishReportCreated(ReportCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                MessagingTopology.EVENTS_EXCHANGE,
                MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                event,
                jsonMetadata(event));
        log.debug("Published ReportCreatedEvent [{}] for report [{}]",
                event.eventId(), event.reportId());
    }

    private MessagePostProcessor jsonMetadata(ReportCreatedEvent event) {
        return message -> {
            MessageProperties properties = message.getMessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setHeader(EVENT_TYPE_HEADER, REPORT_CREATED_EVENT_TYPE);
            properties.setHeader(EVENT_ID_HEADER, event.eventId().toString());
            return message;
        };
    }
}
