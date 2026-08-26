package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.application.port.ProcessedEventRecorder;
import com.cbclean.incident.infrastructure.metrics.IncidentMetrics;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the MDC lifecycle of the consumer: the correlation ID from the
 * RabbitMQ header is visible during processing, cleared afterwards, and never
 * leaks between messages on the reused listener thread.
 */
class ReportCreatedEventConsumerCorrelationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T12:00:00Z");

    private final OpenIncidentUseCase useCase = mock(OpenIncidentUseCase.class);
    private final ProcessedEventRecorder processedEvents = mock(ProcessedEventRecorder.class);
    private final ReportCreatedEventRetryRouter retryRouter = mock(ReportCreatedEventRetryRouter.class);
    private final ReportCreatedEventConsumer consumer =
            new ReportCreatedEventConsumer(useCase, processedEvents, retryRouter, IncidentMetrics.noop());

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private ReportCreatedEvent event(UUID eventId) {
        return new ReportCreatedEvent(
                eventId,
                OCCURRED_AT,
                UUID.randomUUID(),
                "LITTER",
                "LOW",
                null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));
    }

    @Test
    void extractsTheCorrelationIdFromTheHeaderIntoTheMdcDuringProcessing() {
        String correlationId = UUID.randomUUID().toString();
        List<String> seenDuringProcessing = new ArrayList<>();
        when(processedEvents.tryClaim(any(UUID.class), any(String.class))).thenAnswer(invocation -> {
            seenDuringProcessing.add(MDC.get(MessagingTopology.CORRELATION_ID_MDC_KEY));
            return true;
        });

        consumer.onReportCreated(event(UUID.randomUUID()), null, correlationId);

        assertThat(seenDuringProcessing).containsExactly(correlationId);
    }

    @Test
    void clearsTheMdcAfterProcessing() {
        when(processedEvents.tryClaim(any(UUID.class), any(String.class))).thenReturn(true);

        consumer.onReportCreated(event(UUID.randomUUID()), null, UUID.randomUUID().toString());

        assertThat(MDC.get(MessagingTopology.CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void doesNotLeakTheCorrelationIdBetweenConsecutiveMessages() {
        when(processedEvents.tryClaim(any(UUID.class), any(String.class))).thenReturn(true);
        List<String> seenPerMessage = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            seenPerMessage.add(MDC.get(MessagingTopology.CORRELATION_ID_MDC_KEY));
            return mock(com.cbclean.incident.domain.model.Incident.class);
        }).when(useCase).execute(any(OpenIncidentCommand.class));

        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        consumer.onReportCreated(event(UUID.randomUUID()), null, first);
        consumer.onReportCreated(event(UUID.randomUUID()), null, second);

        assertThat(seenPerMessage).containsExactly(first, second);
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void handlesAMessageWithoutCorrelationHeaderWithoutPollutingTheMdc() {
        when(processedEvents.tryClaim(any(UUID.class), any(String.class))).thenReturn(true);

        consumer.onReportCreated(event(UUID.randomUUID()), null, null);

        verify(useCase).execute(any(OpenIncidentCommand.class));
        assertThat(MDC.get(MessagingTopology.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void forwardsTheCorrelationIdToTheRetryRouterOnFailure() {
        org.mockito.Mockito.doThrow(new IllegalStateException("mongodb unavailable"))
                .when(useCase).execute(any(OpenIncidentCommand.class));
        when(processedEvents.tryClaim(any(UUID.class), any(String.class))).thenReturn(true);
        String correlationId = UUID.randomUUID().toString();

        consumer.onReportCreated(event(UUID.randomUUID()), 1, correlationId);

        verify(retryRouter).retryOrDeadLetter(any(ReportCreatedEvent.class),
                eq(1), any(RuntimeException.class), eq(correlationId));
    }

    @Test
    void forwardsTheCorrelationIdToTheDeadLetterPathForPoisonMessages() {
        ReportCreatedEvent poison = new ReportCreatedEvent(
                UUID.randomUUID(), OCCURRED_AT, UUID.randomUUID(),
                "SOMETHING_ELSE", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));
        String correlationId = UUID.randomUUID().toString();

        consumer.onReportCreated(poison, null, correlationId);

        verify(retryRouter).deadLetter(eq(poison), isNull(),
                any(EventTranslationException.class), eq(correlationId));
    }
}
