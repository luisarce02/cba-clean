package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.application.port.ProcessedEventRecorder;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.infrastructure.metrics.IncidentMetrics;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportCreatedEventConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("cccccccc-3333-3333-3333-333333333333");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T11:00:00Z");
    private static final UUID REPORT_ID = UUID.fromString("dddddddd-4444-4444-4444-444444444444");

    private final OpenIncidentUseCase useCase = mock(OpenIncidentUseCase.class);
    private final ProcessedEventRecorder processedEvents = mock(ProcessedEventRecorder.class);
    private final ReportCreatedEventRetryRouter retryRouter = mock(ReportCreatedEventRetryRouter.class);
    private final ReportCreatedEventConsumer consumer =
            new ReportCreatedEventConsumer(useCase, processedEvents, retryRouter, IncidentMetrics.noop());

    private ReportCreatedEvent validEvent() {
        return new ReportCreatedEvent(
                EVENT_ID,
                OCCURRED_AT,
                REPORT_ID,
                "ILLEGAL_DUMPING",
                "CRITICAL",
                "Truck load of rubble in the park",
                new ReportCreatedEvent.Location(48.2345, 16.4166, "Parkweg 7"));
    }

    @Test
    void processesNewEventThroughTheUseCase() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        consumer.onReportCreated(validEvent(), null, null);

        verify(useCase).execute(any(OpenIncidentCommand.class));
    }

    @Test
    void recordsTheProcessedEventWithEventIdAndEventTypeBeforeInvokingTheUseCase() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        consumer.onReportCreated(validEvent(), null, null);

        verify(processedEvents).tryClaim(EVENT_ID, "report.created");
        verify(useCase).execute(any(OpenIncidentCommand.class));
    }

    @Test
    void passesTheExpectedCommandToTheUseCase() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        consumer.onReportCreated(validEvent(), null, null);

        OpenIncidentCommand expected = new OpenIncidentCommand(
                com.cbclean.incident.domain.model.ReportId.fromString(REPORT_ID.toString()),
                IncidentType.ILLEGAL_DUMPING,
                new com.cbclean.incident.domain.model.IncidentLocation(
                        48.2345, 16.4166, "Parkweg 7", null),
                "Truck load of rubble in the park",
                IncidentPriority.CRITICAL);

        verify(useCase).execute(expected);
    }

    @Test
    void mapsReportIdCorrectly() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        consumer.onReportCreated(validEvent(), null, null);

        verify(useCase).execute(argThat(command ->
                command.reportId().value().equals(REPORT_ID)));
    }

    @Test
    void duplicateEventIsSkippedWithoutInvokingTheUseCaseAndWithoutFailing() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(false);

        assertThatCode(() -> consumer.onReportCreated(validEvent(), null, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
    }

    @Test
    void eventIdentityIsTheEventIdNotTheReportId() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        UUID otherReport = UUID.randomUUID();
        ReportCreatedEvent sameEventDifferentReport = new ReportCreatedEvent(
                EVENT_ID, OCCURRED_AT, otherReport, "LITTER", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        consumer.onReportCreated(validEvent(), null, null);
        consumer.onReportCreated(sameEventDifferentReport, null, null);

        verify(processedEvents, org.mockito.Mockito.times(2))
                .tryClaim(EVENT_ID, "report.created");
    }

    @Test
    void differentEventsForTheSameReportAreBothClaimed() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        UUID secondEventId = UUID.randomUUID();
        ReportCreatedEvent differentEventSameReport = new ReportCreatedEvent(
                secondEventId, OCCURRED_AT, REPORT_ID, "LITTER", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        consumer.onReportCreated(validEvent(), null, null);
        consumer.onReportCreated(differentEventSameReport, null, null);

        verify(processedEvents).tryClaim(EVENT_ID, "report.created");
        verify(processedEvents).tryClaim(secondEventId, "report.created");
        verify(useCase, org.mockito.Mockito.times(2)).execute(any(OpenIncidentCommand.class));
    }

    @Test
    void unknownReportTypeIsDeadLetteredInsteadOfRetrying() {
        ReportCreatedEvent event = new ReportCreatedEvent(
                EVENT_ID, OCCURRED_AT, REPORT_ID, "SOMETHING_ELSE", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        consumer.onReportCreated(event, null, null);

        verify(retryRouter).deadLetter(eq(event), isNull(), any(EventTranslationException.class), isNull());
        verifyNoInteractions(useCase);
        verify(processedEvents, never()).tryClaim(any(UUID.class), anyString());
    }

    @Test
    void unknownPriorityIsDeadLetteredInsteadOfRetrying() {
        ReportCreatedEvent event = new ReportCreatedEvent(
                EVENT_ID, OCCURRED_AT, REPORT_ID, "LITTER", "EXTREME", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        consumer.onReportCreated(event, null, null);

        verify(retryRouter).deadLetter(eq(event), isNull(), any(EventTranslationException.class), isNull());
        verifyNoInteractions(useCase);
        verify(processedEvents, never()).tryClaim(any(UUID.class), anyString());
    }

    @Test
    void useCaseFailureOnFirstProcessingIsRoutedToTheRetryChainNotSwallowed() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("persistence failed"))
                .when(useCase).execute(any(OpenIncidentCommand.class));

        consumer.onReportCreated(validEvent(), null, null);

        verify(retryRouter).retryOrDeadLetter(eq(validEvent()), isNull(),
                argThat(cause -> cause.getMessage().contains("persistence failed")), isNull());
        verify(retryRouter, never()).deadLetter(any(), any(), any(), any());
    }

    @Test
    void transientFailureIsRoutedToTheRetryChainInsteadOfPropagating() {
        doThrow(new IllegalStateException("mongodb unavailable"))
                .when(useCase).execute(any(OpenIncidentCommand.class));
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);

        consumer.onReportCreated(validEvent(), 1, null);

        verify(retryRouter).retryOrDeadLetter(eq(validEvent()), eq(1),
                argThat(cause -> cause.getMessage().contains("mongodb unavailable")), isNull());
        verify(retryRouter, never()).deadLetter(any(), any(), any(), any());
    }

    @Test
    void poisonMessageIsRoutedStraightToTheDlqWithoutRetrying() {
        ReportCreatedEvent event = new ReportCreatedEvent(
                EVENT_ID, OCCURRED_AT, REPORT_ID, "SOMETHING_ELSE", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        consumer.onReportCreated(event, null, null);

        verify(retryRouter).deadLetter(eq(event),
                org.mockito.ArgumentMatchers.isNull(),
                any(EventTranslationException.class),
                org.mockito.ArgumentMatchers.isNull());
        verify(retryRouter, never()).retryOrDeadLetter(any(), org.mockito.ArgumentMatchers.any(),
                any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateSkipIsAcknowledgedSilentlyWithoutRouting() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(false);

        assertThatCode(() -> consumer.onReportCreated(validEvent(), null, null))
                .doesNotThrowAnyException();

        verifyNoInteractions(retryRouter);
    }
}

