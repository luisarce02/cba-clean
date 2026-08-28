package com.cbclean.report.infrastructure.messaging;

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

    public MessagingTopologyInitializer(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ((RabbitAdmin) amqpAdmin).initialize();
            log.info("RabbitMQ topology declared: exchange [{}], queue [{}] bound with routing key [{}]",
                    MessagingTopology.EVENTS_EXCHANGE,
                    MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE,
                    MessagingTopology.REPORT_CREATED_ROUTING_KEY);
        } catch (Exception ex) {
            log.warn("RabbitMQ topology declaration deferred: broker not reachable yet", ex);
        }
    }
}
