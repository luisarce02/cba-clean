package com.cbclean.report.infrastructure.outbox;

import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.infrastructure.messaging.MessagingTopology;
import com.cbclean.report.infrastructure.messaging.RabbitReportEventGateway;
import com.cbclean.report.infrastructure.persistence.outbox.OutboxEventRepository;
import com.cbclean.report.infrastructure.persistence.outbox.OutboxJpaRepository;
import com.cbclean.report.infrastructure.persistence.outbox.OutboxStatus;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publisher integration tests against real PostgreSQL and RabbitMQ
 * containers: pending events are published on the existing wire contract,
 * confirmed by the broker before being marked PUBLISHED, and retried when the
 * broker is unavailable.
 */
@SpringBootTest(properties = "cbaclean.outbox.poll-interval=PT1H")
@Testcontainers
class OutboxPublisherIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private OutboxJpaRepository outboxJpa;

    @Autowired
    private SubmitReportUseCase submitReportUseCase;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void cleanState() {
        MDC.clear();
        outboxJpa.deleteAll();
        assertThat(outboxEvents.countPending()).isZero();
    }

    @Test
    void pendingOutboxEventIsPublishedWithTheExistingWireContractAndMarkedPublished()
            throws Exception {
        String correlationId = UUID.randomUUID().toString();
        UUID eventId;
        MDC.put("correlationId", correlationId);
        try {
            eventId = submitReport("Bags of trash near the park");
        } finally {
            MDC.clear();
        }

        outboxPublisher.publishPendingBatch();

        OutboxRow row = rowFor(eventId);
        assertThat(row.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(row.publishedAt()).isNotNull();
        assertThat(outboxEvents.countPending()).isZero();

        try (Connection connection = connectionFactory.createConnection();
             var channel = connection.createChannel(false)) {

            channel.exchangeDeclarePassive(MessagingTopology.EVENTS_EXCHANGE);

            GetResponse response = receive(channel);
            assertThat(response).as("message should arrive in the incident queue").isNotNull();
            assertThat(response.getEnvelope().getExchange())
                    .isEqualTo(MessagingTopology.EVENTS_EXCHANGE);
            assertThat(response.getEnvelope().getRoutingKey())
                    .isEqualTo(MessagingTopology.REPORT_CREATED_ROUTING_KEY)
                    .isEqualTo("report.created");
            assertThat(response.getProps().getDeliveryMode()).isEqualTo(2);
            assertThat(response.getProps().getContentType()).isEqualTo("application/json");
            assertThat(String.valueOf(response.getProps().getHeaders()
                    .get(RabbitReportEventGateway.EVENT_TYPE_HEADER)))
                    .isEqualTo(RabbitReportEventGateway.REPORT_CREATED_EVENT_TYPE);
            assertThat(String.valueOf(response.getProps().getHeaders()
                    .get(RabbitReportEventGateway.EVENT_ID_HEADER)))
                    .isEqualTo(eventId.toString());
            assertThat(String.valueOf(response.getProps().getHeaders()
                    .get(RabbitReportEventGateway.CORRELATION_ID_HEADER)))
                    .isEqualTo(correlationId);

            ReportCreatedEvent payload = objectMapper.readValue(
                    new String(response.getBody(), StandardCharsets.UTF_8),
                    ReportCreatedEvent.class);
            assertThat(payload.eventId()).isEqualTo(eventId);
            assertThat(payload.reportType()).isEqualTo("LITTER");
            assertThat(payload.description()).isEqualTo("Bags of trash near the park");

            // Exactly one message was published for exactly one outbox event.
            assertThat(receive(channel)).isNull();
        }

        assertThat(counterValue(OutboxMetrics.EVENTS_PUBLISHED)).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void unavailableBrokerLeavesTheEventUnpublishedAndItIsRetriedLater()
            throws Exception {
        UUID eventId = submitReport("Waste dumped at the construction site");
        // Simulate an unavailable broker by stopping the RabbitMQ application
        // inside the container: the AMQP listener goes away, the durable
        // topology survives and can be brought back cleanly.
        rabbitmq.execInContainer("rabbitmqctl", "stop_app");
        try {
            outboxPublisher.publishPendingBatch();

            OutboxRow failed = rowFor(eventId);
            assertThat(failed.status()).isEqualTo(OutboxStatus.PENDING);
            assertThat(failed.lastError()).isNotBlank();
            assertThat(failed.attempts()).isEqualTo(1);
            assertThat(failed.publishedAt()).isNull();
            double failuresAfterOutage = counterValue(OutboxMetrics.EVENTS_PUBLISH_FAILURES);
            assertThat(failuresAfterOutage).isGreaterThanOrEqualTo(1.0);

            rabbitmq.execInContainer("rabbitmqctl", "start_app");
            awaitPublished(eventId);

            // The recovery round must not have recorded new failures.
            assertThat(counterValue(OutboxMetrics.EVENTS_PUBLISH_FAILURES))
                    .isEqualTo(failuresAfterOutage);
        } finally {
            ensureBrokerAppRunning();
        }

        OutboxRow row = rowFor(eventId);
        assertThat(row.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(row.publishedAt()).isNotNull();
        assertThat(outboxEvents.countPending()).isZero();
    }

    @Test
    void publishingRoundWithoutPendingEventsIsANoOp() {
        double failuresBefore = counterValue(OutboxMetrics.EVENTS_PUBLISH_FAILURES);

        outboxPublisher.publishPendingBatch();

        assertThat(outboxJpa.count()).isZero();
        assertThat(counterValue(OutboxMetrics.EVENTS_PUBLISH_FAILURES)).isEqualTo(failuresBefore);
    }

    private void ensureBrokerAppRunning() {
        try {
            var state = rabbitmq.execInContainer("rabbitmqctl", "status");
            if (state.getExitCode() != 0) {
                rabbitmq.execInContainer("rabbitmqctl", "start_app");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to verify RabbitMQ broker recovery", e);
        }
    }

    private void awaitPublished(UUID eventId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        while (System.currentTimeMillis() < deadline) {
            outboxPublisher.publishPendingBatch();
            if (rowFor(eventId).status() == OutboxStatus.PUBLISHED) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Outbox event " + eventId + " was not republished after broker recovery");
    }

    private UUID submitReport(String description) {
        submitReportUseCase.execute(new SubmitReportCommand(
                ReportType.LITTER,
                description,
                GeoLocation.of(48.2082, 16.3738),
                null,
                List.of()));
        return outboxJpa.findAll().iterator().next().getId();
    }

    private OutboxRow rowFor(UUID eventId) {
        var entity = outboxJpa.findById(eventId).orElseThrow();
        return new OutboxRow(entity.getStatus(), entity.getLastError(),
                entity.getAttempts(), entity.getPublishedAt());
    }

    private record OutboxRow(OutboxStatus status, String lastError, int attempts,
                             java.time.Instant publishedAt) {
    }

    private double counterValue(String name) {
        return meterRegistry.find(name).counters().stream()
                .mapToDouble(c -> c.count()).sum();
    }

    /**
     * Publishing is asynchronous from the broker's point of view, so poll the
     * queue briefly instead of failing on the first empty get.
     */
    private GetResponse receive(com.rabbitmq.client.Channel channel) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            GetResponse message = GetResponse.get(channel);
            if (message != null) {
                return message;
            }
            Thread.sleep(100);
        }
        return null;
    }

    /** Minimal wrapper mirroring com.rabbitmq.client.GetResponse basicGet usage. */
    private static final class GetResponse {
        private final com.rabbitmq.client.GetResponse delegate;

        private GetResponse(com.rabbitmq.client.GetResponse delegate) {
            this.delegate = delegate;
        }

        static GetResponse get(com.rabbitmq.client.Channel channel) throws Exception {
            com.rabbitmq.client.GetResponse raw =
                    channel.basicGet(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE, true);
            return raw == null ? null : new GetResponse(raw);
        }

        com.rabbitmq.client.Envelope getEnvelope() {
            return delegate.getEnvelope();
        }

        com.rabbitmq.client.AMQP.BasicProperties getProps() {
            return delegate.getProps();
        }

        byte[] getBody() {
            return delegate.getBody();
        }
    }
}
