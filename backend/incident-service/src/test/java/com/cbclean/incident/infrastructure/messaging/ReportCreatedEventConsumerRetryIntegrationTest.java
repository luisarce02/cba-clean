package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.port.ProcessedEventRecorder;
import com.cbclean.incident.infrastructure.persistence.incident.IncidentMongoRepository;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Verifies the bounded retry chain and DLQ behaviour against a real RabbitMQ
 * broker:
 *
 * <ul>
 *   <li>transient failures are retried exactly {@code max-retries} times via
 *   the TTL retry queues, honouring the configured delays, and then land in
 *   the DLQ;</li>
 *   <li>poison messages go straight to the DLQ without retries;</li>
 *   <li>fatally malformed JSON is rejected by the container error handling and
 *   dead-lettered by the main queue's arguments;</li>
 *   <li>successful messages never reach the DLQ.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "incident.messaging.retry.max-retries=3",
        "incident.messaging.retry.delays[0]=200ms",
        "incident.messaging.retry.delays[1]=400ms",
        "incident.messaging.retry.delays[2]=600ms"
})
@Testcontainers
class ReportCreatedEventConsumerRetryIntegrationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T16:00:00Z");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * The claim port is replaced so failures can be simulated at a precise
     * point: before the claim is consumed, i.e. the case retries are actually
     * able to repair (e.g. MongoDB temporarily unavailable).
     */
    @MockitoBean
    private ProcessedEventRecorder processedEvents;

    @Autowired
    private IncidentMongoRepository incidents;

    @BeforeEach
    void resetCollaborators() {
        Mockito.reset(processedEvents);
        incidents.deleteAll();
    }

    @AfterEach
    void purgeInfrastructureQueues() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE);
            channel.queuePurge(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
            for (int retryNumber = 1; retryNumber <= 3; retryNumber++) {
                channel.queuePurge(MessagingTopology.retryQueue(retryNumber));
            }
            return null;
        });
    }

    private ReportCreatedEvent validEvent(UUID eventId) {
        return new ReportCreatedEvent(
                eventId,
                OCCURRED_AT,
                UUID.fromString("dddddddd-9999-9999-9999-999999999999"),
                "BULKY_WASTE",
                "HIGH",
                "Old mattress dumped at the roadside",
                new ReportCreatedEvent.Location(48.2075, 16.3726, "Wienzeile 2"));
    }

    private void publish(ReportCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                MessagingTopology.EVENTS_EXCHANGE,
                MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                event);
    }

    @Test
    void transientFailureIsRetriedTheConfiguredNumberOfTimesWithConfiguredDelaysAndThenDeadLettered()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        RuntimeException mongoDown = new IllegalStateException("mongodb unavailable");
        Mockito.doThrow(mongoDown).when(processedEvents).tryClaim(any(UUID.class), anyString());

        long startedAt = System.nanoTime();
        publish(validEvent(eventId));

        awaitDlqCount(1, Duration.ofSeconds(20));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        long configuredTotalDelay = 200 + 400 + 600;
        assertThat(elapsedMillis)
                .as("the message must pass through all three TTL retry queues before the DLQ")
                .isGreaterThanOrEqualTo(configuredTotalDelay);

        Mockito.verify(processedEvents, Mockito.timeout(5_000).times(4))
                .tryClaim(any(UUID.class), anyString());

        GetResponse dead = basicGet(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
        assertThat(dead).isNotNull();
        String payload = new String(dead.getBody(), StandardCharsets.UTF_8);
        assertThat(payload).contains(eventId.toString());
        Object retryHeader = dead.getProps().getHeaders().get(MessagingTopology.RETRY_COUNT_HEADER);
        assertThat(((Number) retryHeader).intValue())
                .as("the DLQ copy carries the exhausted retry counter").isEqualTo(3);

        assertThat(queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE)).isZero();
        for (int retryNumber = 1; retryNumber <= 3; retryNumber++) {
            assertThat(queueMessageCount(MessagingTopology.retryQueue(retryNumber))).isZero();
        }
        assertThat(incidents.count()).isZero();
    }

    @Test
    void poisonEventIsDeadLetteredImmediatelyWithoutRetrying() {
        Mockito.when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        publish(new ReportCreatedEvent(
                UUID.randomUUID(),
                OCCURRED_AT,
                UUID.fromString("eeeeeeee-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "LITTER",
                "EXTREME",
                "Unknown priority must be dead-lettered",
                new ReportCreatedEvent.Location(48.2, 16.4, null)));

        awaitDlqCount(1, Duration.ofSeconds(10));

        GetResponse dead = basicGet(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("EXTREME");
        Object retryHeader = dead.getProps().getHeaders().get(MessagingTopology.RETRY_COUNT_HEADER);
        assertThat(retryHeader == null ? 0 : ((Number) retryHeader).intValue()).isZero();

        Mockito.verify(processedEvents, Mockito.never()).tryClaim(any(UUID.class), anyString());
        assertThat(incidents.count()).isZero();
    }

    @Test
    void fatallyMalformedJsonNeverReachesTheListenerAndIsDeadLetteredByTheMainQueue() {
        rabbitTemplate.execute(channel -> {
            channel.basicPublish(
                    MessagingTopology.EVENTS_EXCHANGE,
                    MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                    null,
                    "{ this is not json".getBytes(StandardCharsets.UTF_8));
            return null;
        });

        awaitDlqCount(1, Duration.ofSeconds(10));

        Mockito.verify(processedEvents, Mockito.never()).tryClaim(any(UUID.class), anyString());
        assertThat(queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE)).isZero();
        assertThat(incidents.count()).isZero();
    }

    @Test
    void successfulMessagesAreAcknowledgedAndNeverSentToTheDlq() throws Exception {
        Mockito.when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        UUID eventId = UUID.randomUUID();
        publish(validEvent(eventId));

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline && incidents.count() == 0) {
            Thread.sleep(200);
        }
        assertThat(incidents.count())
                .as("successful delivery opens exactly one incident").isEqualTo(1);

        awaitQueueDrained(Duration.ofSeconds(10));

        assertThat(queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE)).isZero();
        assertThat(queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ))
                .as("success must not dead-letter").isZero();
        Mockito.verify(processedEvents, Mockito.times(1)).tryClaim(any(UUID.class), anyString());
    }

    private void awaitDlqCount(int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Integer last = null;
        while (System.nanoTime() < deadline) {
            last = queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ);
            if (last != null && last >= expected) {
                return;
            }
            sleepUnchecked(200);
        }
        throw new AssertionError("Expected DLQ to reach " + expected
                + " message(s) within " + timeout.toSeconds() + "s, found " + last);
    }

    private void awaitQueueDrained(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE) == 0) {
                return;
            }
            sleepUnchecked(200);
        }
    }

    private int queueMessageCount(String queueName) {
        Long count = rabbitTemplate.execute(channel -> channel.messageCount(queueName));
        return count == null ? -1 : count.intValue();
    }

    private GetResponse basicGet(String queueName) {
        return rabbitTemplate.execute(channel -> channel.basicGet(queueName, true));
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

