package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

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

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @MockitoBean
    private OpenIncidentUseCase openIncidentUseCase;

    @Test
    void failedProcessingLeavesTheMessageInQueueInsteadOfAcknowledgingIt() {
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

        Mockito.verify(openIncidentUseCase, Mockito.timeout(10_000).atLeastOnce())
                .execute(any(OpenIncidentCommand.class));

        listenerRegistry.stop();
        sleepUnchecked(2_000);

        Long remaining = rabbitTemplate.execute(channel ->
                channel.messageCount(MessagingTopology.INCIDENT_REPORT_CREATED_QUEUE));
        assertThat(remaining).as("message must not be acknowledged on failure").isEqualTo(1);
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
