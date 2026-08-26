package com.cbclean.report.infrastructure.messaging;

import com.cbclean.report.application.port.ReportEventPublisher;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.slf4j.MDC;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the correlation ID held in the MDC (placed there by the HTTP
 * correlation filter during request processing) is propagated into the
 * {@code correlationId} header of the published RabbitMQ message.
 */
@SpringBootTest
@Testcontainers
class RabbitReportEventPublisherCorrelationIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ReportEventPublisher reportEventPublisher;

    @Autowired
    private ConnectionFactory connectionFactory;

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesTheCorrelationIdFromTheMdcIntoTheMessageHeader() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        ReportCreatedEvent event = ReportCreatedEvent.of(
                UUID.randomUUID(),
                Instant.parse("2026-08-25T12:00:00Z"),
                UUID.randomUUID(),
                "LITTER",
                "NORMAL",
                null,
                48.2,
                16.3,
                null);

        MDC.put("correlationId", correlationId);
        reportEventPublisher.publishReportCreated(event);

        try (Connection connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {
            com.rabbitmq.client.GetResponse response = receive(channel);
            assertThat(response).as("message should arrive in the incident queue").isNotNull();
            assertThat(String.valueOf(response.getProps().getHeaders()
                    .get(RabbitReportEventPublisher.CORRELATION_ID_HEADER)))
                    .isEqualTo(correlationId);
        }
    }

    @Test
    void omitsTheCorrelationIdHeaderWhenNoCorrelationContextExists() throws Exception {
        ReportCreatedEvent event = ReportCreatedEvent.of(
                UUID.randomUUID(),
                Instant.parse("2026-08-25T12:00:00Z"),
                UUID.randomUUID(),
                "LITTER",
                "LOW",
                null,
                48.2,
                16.4,
                null);

        reportEventPublisher.publishReportCreated(event);

        try (Connection connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {
            com.rabbitmq.client.GetResponse response = receive(channel);
            assertThat(response).as("message should arrive in the incident queue").isNotNull();
            assertThat(response.getProps().getHeaders()
                    .getOrDefault(RabbitReportEventPublisher.CORRELATION_ID_HEADER, null)).isNull();
        }
    }

    private com.rabbitmq.client.GetResponse receive(com.rabbitmq.client.Channel channel)
            throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            com.rabbitmq.client.GetResponse message = channel.basicGet(
                    MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE, true);
            if (message != null) {
                return message;
            }
            Thread.sleep(100);
        }
        return null;
    }
}
