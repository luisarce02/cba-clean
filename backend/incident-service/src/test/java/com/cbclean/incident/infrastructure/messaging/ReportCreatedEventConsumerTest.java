package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ReportCreatedEventConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("cccccccc-3333-3333-3333-333333333333");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T11:00:00Z");
    private static final UUID REPORT_ID = UUID.fromString("dddddddd-4444-4444-4444-444444444444");

    private final OpenIncidentUseCase useCase = mock(OpenIncidentUseCase.class);
    private final ReportCreatedEventConsumer consumer = new ReportCreatedEventConsumer(useCase);

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
    void invokesUseCaseWithCommandMappedFromTheEvent() {
        consumer.onReportCreated(validEvent());

        verify(useCase).execute(any(OpenIncidentCommand.class));
    }

    @Test
    void passesTheExpectedCommandToTheUseCase() {
        consumer.onReportCreated(validEvent());

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
        consumer.onReportCreated(validEvent());

        verify(useCase).execute(org.mockito.ArgumentMatchers.argThat(command ->
                command.reportId().value().equals(REPORT_ID)));
    }

    @Test
    void unknownReportTypeNeverReachesTheUseCase() {
        ReportCreatedEvent event = new ReportCreatedEvent(
                EVENT_ID, OCCURRED_AT, REPORT_ID, "SOMETHING_ELSE", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        assertThatThrownBy(() -> consumer.onReportCreated(event))
                .isInstanceOf(EventTranslationException.class);

        verifyNoInteractions(useCase);
    }

    @Test
    void unknownPriorityNeverReachesTheUseCase() {
        ReportCreatedEvent event = new ReportCreatedEvent(
                EVENT_ID, OCCURRED_AT, REPORT_ID, "LITTER", "EXTREME", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        assertThatThrownBy(() -> consumer.onReportCreated(event))
                .isInstanceOf(EventTranslationException.class);

        verifyNoInteractions(useCase);
    }

    @Test
    void useCaseFailurePropagatesInsteadOfBeingSwallowed() {
        doThrow(new IllegalStateException("persistence failed"))
                .when(useCase).execute(any(OpenIncidentCommand.class));

        assertThatThrownBy(() -> consumer.onReportCreated(validEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persistence failed");
    }
}
