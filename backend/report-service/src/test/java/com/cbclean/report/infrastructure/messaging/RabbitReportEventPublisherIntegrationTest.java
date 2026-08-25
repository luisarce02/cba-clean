package com.cbclean.report.infrastructure.messaging;

import com.cbclean.report.application.port.ReportEventPublisher;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RabbitReportEventPublisherIntegrationTest {

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

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void publishedReportCreatedEventReachesIncidentQueueAsDeserializableJson() throws Exception {
        ReportCreatedEvent event = ReportCreatedEvent.of(
                UUID.randomUUID(),
                Instant.parse("2026-08-25T12:00:00Z"),
                UUID.randomUUID(),
                "ILLEGAL_DUMPING",
                "HIGH",
                "Waste dumped next to the river",
                48.2082,
                16.3738,
                "Danube riverside, Vienna");

        reportEventPublisher.publishReportCreated(event);

        try (Connection connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {

            channel.exchangeDeclarePassive(MessagingTopology.EVENTS_EXCHANGE);

            com.rabbitmq.client.GetResponse response = receive(channel);
            assertThat(response).as("message should arrive in the incident queue").isNotNull();

            assertThat(response.getEnvelope().getRoutingKey())
                    .isEqualTo(MessagingTopology.REPORT_CREATED_ROUTING_KEY)
                    .isEqualTo("report.created");
            assertThat(response.getProps().getDeliveryMode())
                    .as("messages must be persistent")
                    .isEqualTo(2);
            assertThat(response.getProps().getContentType()).isEqualTo("application/json");
            assertThat(String.valueOf(response.getProps().getHeaders()
                    .get(RabbitReportEventPublisher.EVENT_TYPE_HEADER)))
                    .isEqualTo(RabbitReportEventPublisher.REPORT_CREATED_EVENT_TYPE);
            assertThat(String.valueOf(response.getProps().getHeaders()
                    .get(RabbitReportEventPublisher.EVENT_ID_HEADER)))
                    .isEqualTo(event.eventId().toString());

            ReportCreatedEvent deserialized = objectMapper.readValue(
                    new String(response.getBody(), StandardCharsets.UTF_8),
                    ReportCreatedEvent.class);

            assertThat(deserialized.eventId()).isEqualTo(event.eventId());
            assertThat(deserialized.occurredAt()).isEqualTo(event.occurredAt());
            assertThat(deserialized.reportId()).isEqualTo(event.reportId());
            assertThat(deserialized.reportType()).isEqualTo("ILLEGAL_DUMPING");
            assertThat(deserialized.priority()).isEqualTo("HIGH");
            assertThat(deserialized.description()).isEqualTo("Waste dumped next to the river");
            assertThat(deserialized.location().latitude()).isEqualTo(48.2082);
            assertThat(deserialized.location().longitude()).isEqualTo(16.3738);
            assertThat(deserialized.location().address()).isEqualTo("Danube riverside, Vienna");
        }
    }

    /**
     * Publishing is asynchronous from the broker's point of view, so poll the
     * queue briefly instead of failing on the first empty get.
     */
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
