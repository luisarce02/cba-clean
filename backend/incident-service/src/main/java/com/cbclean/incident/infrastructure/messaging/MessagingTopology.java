package com.cbclean.incident.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized RabbitMQ topology for CBA Clean messaging.
 *
 * <p>Declarations are explicit and idempotent so that both services converge on
 * the same topology regardless of startup order.</p>
 *
 * <h2>Retry / dead-letter topology</h2>
 *
 * <pre>
 * cba-clean.events --report.created--&gt; incident-service.report-created   (main queue)
 *                                        |  failure (consumer-managed)
 *                                        v
 *                                     cba-clean.dlx (dead-letter exchange)
 *                                        |--incident-service.report-created.retry.1--&gt; retry.1 (TTL delays[0])
 *                                        |--incident-service.report-created.retry.2--&gt; retry.2 (TTL delays[1])
 *                                        |--incident-service.report-created.retry.3--&gt; retry.3 (TTL delays[2])
 *                                        |        each expiry dead-letters back to cba-clean.events/report.created
 *                                        |--incident-service.report-created.dlq-----&gt; DLQ (terminal)
 * </pre>
 *
 * <p>The main queue is declared with {@code x-dead-letter-exchange =
 * cba-clean.dlx} and {@code x-dead-letter-routing-key = ...dlq}, so messages
 * rejected without requeue by the container itself (e.g. fatally malformed JSON
 * that never reaches the listener) land straight in the DLQ. All queues and
 * exchanges are durable.</p>
 */
@Configuration
@EnableConfigurationProperties(IncidentMessagingRetryProperties.class)
public class MessagingTopology {

    public static final String EVENTS_EXCHANGE = "cba-clean.events";
    public static final String REPORT_CREATED_ROUTING_KEY = "report.created";
    public static final String INCIDENT_REPORT_CREATED_QUEUE = "incident-service.report-created";

    public static final String DEAD_LETTER_EXCHANGE = "cba-clean.dlx";
    public static final String INCIDENT_REPORT_CREATED_DLQ = "incident-service.report-created.dlq";

    /** Consumer-managed counter of retries already performed for a message. */
    public static final String RETRY_COUNT_HEADER = "x-retry-count";

    /** Header carrying the correlation ID stamped by the Report Service. */
    public static final String CORRELATION_ID_HEADER = "correlationId";

    /** SLF4J MDC key under which the correlation ID is exposed to logging. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    public static String retryQueue(int retryNumber) {
        return INCIDENT_REPORT_CREATED_QUEUE + ".retry." + retryNumber;
    }

    private final IncidentMessagingRetryProperties retryProperties;

    public MessagingTopology(IncidentMessagingRetryProperties retryProperties) {
        this.retryProperties = retryProperties;
    }

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

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange(DEAD_LETTER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * DLQ plus the bounded TTL retry chain: retry queue {@code i} waits
     * {@code delays[i-1]} and then dead-letters the message back to the main
     * exchange with the original routing key, producing a delayed redelivery.
     */
    @Bean
    public Declarables retryAndDeadLetterInfrastructure() {
        List<org.springframework.amqp.core.Declarable> declarables = new ArrayList<>();

        declarables.add(QueueBuilder.durable(INCIDENT_REPORT_CREATED_DLQ).build());

        declarables.add(BindingBuilder.bind(new Queue(INCIDENT_REPORT_CREATED_DLQ))
                .to(deadLetterExchange())
                .with(INCIDENT_REPORT_CREATED_DLQ));

        for (int retryNumber = 1; retryNumber <= retryProperties.getMaxRetries(); retryNumber++) {
            long ttlMillis = retryProperties.getDelays().get(retryNumber - 1).toMillis();

            declarables.add(QueueBuilder.durable(retryQueue(retryNumber))
                    .ttl((int) ttlMillis)
                    .deadLetterExchange(EVENTS_EXCHANGE)
                    .deadLetterRoutingKey(REPORT_CREATED_ROUTING_KEY)
                    .build());

            declarables.add(BindingBuilder.bind(new Queue(retryQueue(retryNumber)))
                    .to(deadLetterExchange())
                    .with(retryQueue(retryNumber)));
        }

        return new Declarables(declarables);
    }
}
