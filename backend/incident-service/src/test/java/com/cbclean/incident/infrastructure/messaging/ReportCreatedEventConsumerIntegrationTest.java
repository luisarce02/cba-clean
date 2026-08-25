package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.infrastructure.persistence.incident.IncidentDocument;
import com.cbclean.incident.infrastructure.persistence.incident.IncidentMongoRepository;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ReportCreatedEventConsumerIntegrationTest {

    private static final UUID EVENT_ID = UUID.fromString("eeeeeeee-5555-5555-5555-555555555555");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T12:00:00Z");
    private static final UUID REPORT_ID = UUID.fromString("ffffffff-6666-6666-6666-666666666666");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private IncidentMongoRepository incidents;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void cleanPersistence() {
        incidents.deleteAll();
    }

    @Test
    void consumesPublishedReportCreatedEventAndPersistsTheIncident() {
        ReportCreatedEvent event = new ReportCreatedEvent(
                EVENT_ID,
                OCCURRED_AT,
                REPORT_ID,
                "BULKY_WASTE",
                "HIGH",
                "Old sofa dumped next to the containers",
                new ReportCreatedEvent.Location(48.2082, 16.3738, "Naschmarkt 3"));

        rabbitTemplate.convertAndSend(
                MessagingTopology.EVENTS_EXCHANGE,
                MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                event);

        IncidentDocument saved = awaitSingleIncident();
        assertThat(saved.getReportId()).isEqualTo(REPORT_ID.toString());
        assertThat(saved.getType()).isEqualTo("BULKY_WASTE");
        assertThat(saved.getPriority()).isEqualTo("HIGH");
        assertThat(saved.getStatus()).isEqualTo("NEW");
        assertThat(saved.getDescription()).isEqualTo("Old sofa dumped next to the containers");
        assertThat(saved.getLocation().latitude()).isEqualTo(48.2082);
        assertThat(saved.getLocation().longitude()).isEqualTo(16.3738);
        assertThat(saved.getLocation().address()).isEqualTo("Naschmarkt 3");

        assertThat(queueMessageCount()).isZero();
    }

    /**
     * Verifies the production scenario of messages already waiting durably in
     * {@code incident-service.report-created}: the event is published while the
     * listener is stopped, waits in the queue, and must be processed once
     * consumption begins.
     */
    @Test
    void processesMessageThatWasWaitingInQueueBeforeConsumptionStarted()
            throws Exception {
        listenerRegistry.stop();

        try {
            ReportCreatedEvent queued = new ReportCreatedEvent(
                    EVENT_ID,
                    OCCURRED_AT,
                    REPORT_ID,
                    "OVERFLOWING_BIN",
                    "NORMAL",
                    "Bin has not been emptied for two weeks",
                    new ReportCreatedEvent.Location(48.2109, 16.3794, "Kärntner Straße 15"));

            rabbitTemplate.convertAndSend(
                    MessagingTopology.EVENTS_EXCHANGE,
                    MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                    queued);

            assertThat(awaitQueueMessageCount(Duration.ofSeconds(5))).isEqualTo(1);
        } finally {
            listenerRegistry.start();
        }

        IncidentDocument saved = awaitSingleIncident();
        assertThat(saved.getType()).isEqualTo("OVERFLOWING_BIN");
        assertThat(saved.getPriority()).isEqualTo("NORMAL");
        assertThat(queueMessageCount()).isZero();
    }

    private IncidentDocument awaitSingleIncident() {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (incidents.count() == 1) {
                return incidents.findAll().iterator().next();
            }
            sleepUnchecked(200);
        }
        throw new AssertionError(
                "Expected exactly one persisted incident within 10s, found " + incidents.count());
    }

    private int queueMessageCount() {
        Long count = rabbitTemplate.execute(channel ->
                channel.messageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE));
        return count == null ? -1 : count.intValue();
    }

    private int awaitQueueMessageCount(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            int count = queueMessageCount();
            if (count > 0) {
                return count;
            }
            sleepUnchecked(200);
        }
        return queueMessageCount();
    }

    private static void sleepUnchecked(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
