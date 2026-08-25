package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.port.ProcessedEventRecorder;
import com.cbclean.incident.infrastructure.persistence.incident.IncidentMongoRepository;
import com.cbclean.incident.infrastructure.persistence.processedevent.ProcessedEventMongoRepository;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ReportCreatedEventConsumerIdempotencyIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T14:00:00Z");

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
    private ProcessedEventMongoRepository processedEvents;

    @Autowired
    private ProcessedEventRecorder eventRecorder;

    @BeforeEach
    void cleanPersistence() {
        incidents.deleteAll();
        processedEvents.deleteAll();
    }

    private ReportCreatedEvent event(UUID eventId, UUID reportId) {
        return new ReportCreatedEvent(
                eventId,
                OCCURRED_AT,
                reportId,
                "BULKY_WASTE",
                "HIGH",
                "Old sofa dumped next to the containers",
                new ReportCreatedEvent.Location(48.2082, 16.3738, "Naschmarkt 3"));
    }

    private void publish(ReportCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                MessagingTopology.EVENTS_EXCHANGE,
                MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                event);
    }

    @Test
    void publishingTheSameEventTwiceCreatesExactlyOneIncidentAndAcknowledgesBothMessages() {
        UUID eventId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();

        publish(event(eventId, reportId));
        publish(event(eventId, reportId));

        awaitIncidentCount(1);
        awaitQueueDrained(Duration.ofSeconds(10));

        assertThat(processedEvents.count())
                .as("exactly one processed-event record for the duplicate pair")
                .isEqualTo(1);
    }

    @Test
    void eventIdentityIsTheEventIdNotTheReportId() {
        UUID reportId = UUID.randomUUID();

        publish(event(UUID.randomUUID(), reportId));
        publish(event(UUID.randomUUID(), reportId));

        awaitIncidentCount(2);
        assertThat(processedEvents.count()).isEqualTo(2);
    }

    @Test
    void concurrentClaimsOfTheSameEventIdYieldExactlyOneWinner() throws Exception {
        UUID eventId = UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> racers = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                racers.add(() -> eventRecorder.tryClaim(eventId, "report.created"));
            }
            List<Future<Boolean>> outcomes = pool.invokeAll(racers);

            int winners = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get()) {
                    winners++;
                }
            }
            assertThat(winners)
                    .as("the unique eventId constraint lets exactly one racer win")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(processedEvents.count()).isEqualTo(1);
    }

    private void awaitIncidentCount(long expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (incidents.count() == expected && queueMessageCount() == 0) {
                return;
            }
            sleepUnchecked(200);
        }
        throw new AssertionError("Expected " + expected
                + " incident(s) within 10s, found " + incidents.count());
    }

    private Long queueMessageCount() {
        return rabbitTemplate.execute(channel ->
                channel.messageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE));
    }

    private void awaitQueueDrained(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (queueMessageCount() == 0) {
                return;
            }
            sleepUnchecked(200);
        }
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
