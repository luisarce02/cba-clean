package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.port.ProcessedEventRecorder;
import com.cbclean.incident.infrastructure.persistence.incident.IncidentMongoRepository;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * End-to-end correlation check against a real RabbitMQ broker: the consumer
 * must extract the {@code correlationId} header of the delivered message into
 * its logging context while processing, create exactly one incident, and leave
 * no MDC residue between messages.
 */
@SpringBootTest
@Testcontainers
class ReportCreatedEventConsumerCorrelationIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T12:00:00Z");

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

    /**
     * The claim is the first point where the event reaches application code, so
     * the MDC state observed there reflects exactly what the consumer extracted.
     */
    @MockitoBean
    private ProcessedEventRecorder processedEvents;

    private final List<String> correlationIdsSeenDuringProcessing = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        incidents.deleteAll();
        Mockito.reset(processedEvents);
        correlationIdsSeenDuringProcessing.clear();
        Mockito.when(processedEvents.tryClaim(any(UUID.class), anyString()))
                .thenAnswer(invocation -> {
                    String correlationId = MDC.get(MessagingTopology.CORRELATION_ID_MDC_KEY);
                    correlationIdsSeenDuringProcessing.add(correlationId);
                    return true;
                });
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void extractsTheCorrelationIdHeaderIntoTheMdcAndCreatesExactlyOneIncident() {
        String correlationId = UUID.randomUUID().toString();

        publish(event(UUID.randomUUID()), message -> {
            message.getMessageProperties().setHeader(
                    MessagingTopology.CORRELATION_ID_HEADER, correlationId);
            return message;
        });

        awaitIncidents(1, Duration.ofSeconds(10));
        assertThat(correlationIdsSeenDuringProcessing).containsExactly(correlationId);
        assertThat(incidents.count()).isEqualTo(1);
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void doesNotLeakCorrelationContextBetweenConsecutivelyConsumedMessages() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        publish(event(UUID.randomUUID()), header(first));
        awaitIncidents(1, Duration.ofSeconds(10));
        publish(event(UUID.randomUUID()), header(second));
        awaitIncidents(2, Duration.ofSeconds(10));

        assertThat(correlationIdsSeenDuringProcessing).containsExactly(first, second);
        assertThat(incidents.count()).isEqualTo(2);
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    private MessagePostProcessor header(String correlationId) {
        return message -> {
            message.getMessageProperties().setHeader(
                    MessagingTopology.CORRELATION_ID_HEADER, correlationId);
            return message;
        };
    }

    private ReportCreatedEvent event(UUID eventId) {
        return new ReportCreatedEvent(
                eventId,
                OCCURRED_AT,
                UUID.randomUUID(),
                "BULKY_WASTE",
                "HIGH",
                "Old sofa dumped next to the containers",
                new ReportCreatedEvent.Location(48.2082, 16.3738, "Naschmarkt 3"));
    }

    private void publish(ReportCreatedEvent event, MessagePostProcessor metadata) {
        rabbitTemplate.convertAndSend(
                MessagingTopology.EVENTS_EXCHANGE,
                MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                event,
                metadata);
    }

    private void awaitIncidents(int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && incidents.count() < expected) {
            sleepUnchecked(200);
        }
        if (incidents.count() < expected) {
            throw new AssertionError("Expected " + expected + " incident(s) within "
                    + timeout.toSeconds() + "s, found " + incidents.count());
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
