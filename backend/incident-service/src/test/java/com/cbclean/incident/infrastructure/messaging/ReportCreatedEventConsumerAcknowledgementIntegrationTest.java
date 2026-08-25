package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.infrastructure.persistence.incident.IncidentMongoRepository;
import com.cbclean.incident.infrastructure.persistence.processedevent.ProcessedEventMongoRepository;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Documents the interaction between processing failure, bounded retries and
 * idempotency: the first delivery wins the claim and then fails inside the use
 * case. The retry chain redelivers the message, but the redelivery is skipped
 * as an already-claimed event - acknowledged without invoking the use case
 * again and without dead-lettering.
 *
 * <p>This is the documented failure window of claim-first idempotency: no
 * incident is created for such an event. Retries help failures up to and
 * including the claim itself (e.g. MongoDB unavailable); they cannot repair a
 * failure that happens after the claim was consumed. Eliminating the window
 * would require transactional incident+claim writes or an outbox pattern.</p>
 */
@SpringBootTest(properties = {
        "incident.messaging.retry.max-retries=3",
        "incident.messaging.retry.delays[0]=100ms",
        "incident.messaging.retry.delays[1]=200ms",
        "incident.messaging.retry.delays[2]=300ms"
})
@Testcontainers
class ReportCreatedEventConsumerAcknowledgementIntegrationTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-7777-7777-7777-777777777777");
    private static final UUID REPORT_ID = UUID.fromString("22222222-8888-8888-8888-888888888888");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private OpenIncidentUseCase openIncidentUseCase;

    @Autowired
    private IncidentMongoRepository incidents;

    @Autowired
    private ProcessedEventMongoRepository processedEvents;

    @Test
    void failedProcessingIsRetriedThenSkippedAsClaimedWithoutCreatingAnIncidentOrDeadLettering() {
        Mockito.doThrow(new IllegalStateException("simulated persistence failure"))
                .when(openIncidentUseCase).execute(any(OpenIncidentCommand.class));

        ReportCreatedEvent event = new ReportCreatedEvent(
                EVENT_ID,
                Instant.parse("2026-08-25T13:00:00Z"),
                REPORT_ID,
                "LITTER",
                "LOW",
                "Broken glass on the cycle path",
                new ReportCreatedEvent.Location(48.1985, 16.3519, "Rechte Wienzeile 6"));

        rabbitTemplate.convertAndSend(
                MessagingTopology.EVENTS_EXCHANGE,
                MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                event);

        Mockito.verify(openIncidentUseCase, Mockito.timeout(10_000).times(1))
                .execute(any(OpenIncidentCommand.class));

        awaitQueueDrained(Duration.ofSeconds(15));
        awaitRetryQueuesDrained(Duration.ofSeconds(15));

        assertThat(incidents.count()).isZero();
        assertThat(processedEvents.count())
                .as("the failed delivery consumed the single claim").isEqualTo(1);
        assertThat(queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE))
                .as("redelivered message skipped as claimed and acknowledged").isZero();
        assertThat(queueMessageCount(MessagingTopology.INCIDENT_REPORT_CREATED_DLQ))
                .as("duplicate skip is a successful outcome, not a dead letter").isZero();
        Mockito.verify(openIncidentUseCase, Mockito.times(1))
                .execute(any(OpenIncidentCommand.class));
    }

    private void awaitQueueDrained(Duration timeout) {
        awaitQueueCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE, 0, timeout);
    }

    private void awaitRetryQueuesDrained(Duration timeout) {
        for (int retryNumber = 1; retryNumber <= 3; retryNumber++) {
            awaitQueueCount(MessagingTopology.retryQueue(retryNumber), 0, timeout);
        }
    }

    private void awaitQueueCount(String queueName, int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Long last = null;
        while (System.nanoTime() < deadline) {
            last = queueMessageCount(queueName);
            if (last != null && last == expected) {
                return;
            }
            sleepUnchecked(200);
        }
        throw new AssertionError("Expected queue [" + queueName + "] to reach " + expected
                + " message(s) within " + timeout.toSeconds() + "s, found " + last);
    }

    private Long queueMessageCount(String queueName) {
        return rabbitTemplate.execute(channel -> channel.messageCount(queueName));
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
