package com.cbclean.incident.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Declares the messaging topology eagerly on application startup.
 *
 * <p>Failures are tolerated (logged as a warning) so the application can still
 * start without a reachable RabbitMQ broker; declarations are retried by
 * RabbitAdmin whenever a connection is later opened.</p>
 */
@Component
public class MessagingTopologyInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MessagingTopologyInitializer.class);

    private final AmqpAdmin amqpAdmin;
    private final IncidentMessagingRetryProperties retryProperties;

    public MessagingTopologyInitializer(AmqpAdmin amqpAdmin,
                                        IncidentMessagingRetryProperties retryProperties) {
        this.amqpAdmin = amqpAdmin;
        this.retryProperties = retryProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ((RabbitAdmin) amqpAdmin).initialize();
            log.info("RabbitMQ topology declared: exchange [{}], queue [{}] bound with routing key [{}], "
                            + "DLX [{}], DLQ [{}], {} TTL retry queue(s)",
                    MessagingTopology.EVENTS_EXCHANGE,
                    MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE,
                    MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                    MessagingTopology.DEAD_LETTER_EXCHANGE,
                    MessagingTopology.INCIDENT_REPORT_CREATED_DLQ,
                    retryProperties.getMaxRetries());
        } catch (Exception ex) {
            log.warn("RabbitMQ topology declaration deferred: broker not reachable yet ({})",
                    ex.getMessage());
        }
    }
}
