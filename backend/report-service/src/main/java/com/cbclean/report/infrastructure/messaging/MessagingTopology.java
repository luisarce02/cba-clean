package com.cbclean.report.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized RabbitMQ topology for CBA Clean messaging.
 *
 * <p>Declarations are explicit and idempotent so that both services converge on
 * the same topology regardless of startup order.</p>
 */
@Configuration
public class MessagingTopology {

    public static final String EVENTS_EXCHANGE = "cba-clean.events";
    public static final String REPORT_CREATED_ROUTING_KEY = "report.created";
    public static final String INCIDENT_REPORT_CREATED_QUEUE = "incident-service.report-created";

    /**
     * Must mirror the Incident Service's declarations exactly: RabbitMQ rejects
     * redeclaration of an existing durable queue with different arguments.
     */
    public static final String DEAD_LETTER_EXCHANGE = "cba-clean.dlx";
    public static final String INCIDENT_REPORT_CREATED_DLQ = "incident-service.report-created.dlq";

    @Bean
    public TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue incidentReportCreatedQueue() {
        return QueueBuilder.durable(INCIDENT_REPORT_CREATED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(INCIDENT_REPORT_CREATED_DLQ)
                .build();
    }

    @Bean
    public Binding reportCreatedBinding(Queue incidentReportCreatedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(incidentReportCreatedQueue)
                .to(eventsExchange)
                .with(REPORT_CREATED_ROUTING_KEY);
    }
}
