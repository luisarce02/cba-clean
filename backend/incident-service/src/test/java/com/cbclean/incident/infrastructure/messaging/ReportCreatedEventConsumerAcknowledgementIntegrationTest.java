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

import java.time.Instant;
import java.util.UUID;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * Documents the interaction between processing failure and idempotency: the
 * first delivery claims the event and then fails inside the use case; the
 * redelivered message is skipped as already claimed and acknowledged.
 *
 * <p>This is the documented failure window of at-most-once event processing:
 * no incident is created for the failed event. Eliminating the window would
 * require transactional incident+claim writes or an outbox pattern.</p>
 */
@SpringBootTest
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
    void failedProcessingIsEventuallyAcknowledgedWithoutCreatingAnIncidentOrRetrying() {
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

        awaitQueueDrained(Duration.ofSeconds(10));

        assertThat(incidents.count()).isZero();
        assertThat(processedEvents.count())
                .as("the failed delivery still consumed the single claim").isEqualTo(1);

        Long remaining = queueMessageCount();
        assertThat(remaining).as("redelivered message skipped and acknowledged").isZero();
        Mockito.verify(openIncidentUseCase, Mockito.times(1))
                .execute(any(OpenIncidentCommand.class));
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

    private Long queueMessageCount() {
        return rabbitTemplate.execute(channel ->
                channel.messageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE));
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
