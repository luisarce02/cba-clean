package com.cbclean.report.application.report.submit;

import com.cbclean.report.application.correlation.CorrelationContext;
import com.cbclean.report.application.outbox.OutboxEntry;
import com.cbclean.report.application.port.OutboxStore;
import com.cbclean.report.application.port.ReportMetrics;
import com.cbclean.report.application.port.UnitOfWork;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.InvalidReportException;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportPriority;
import com.cbclean.report.domain.model.ReportStatus;
import com.cbclean.report.domain.repository.ReportRepository;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.model.Reporter;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SubmitReportUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    private final ReportRepository repository = mock(ReportRepository.class);
    private final OutboxStore outbox = mock(OutboxStore.class);
    private final SubmitReportUseCase useCase = new SubmitReportUseCase(
            repository, outbox, UnitOfWork.identity(), Clock.fixed(NOW, ZoneOffset.UTC), ReportMetrics.noop());

    @AfterEach
    void clearCorrelationContext() {
        MDC.clear();
    }

    @Test
    void validSubmissionSavesAndReturnsReport() {
        SubmitReportCommand command = new SubmitReportCommand(
                ReportType.ILLEGAL_DUMPING,
                "  Waste dumped next to the river  ",
                GeoLocation.of(48.2082, 16.3738),
                null,
                List.of("photo-1"));

        Report result = useCase.execute(command);

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(repository).save(saved.capture());
        assertThat(result).isSameAs(saved.getValue());
        assertThat(result.id()).isNotNull();
        assertThat(result.type()).isEqualTo(ReportType.ILLEGAL_DUMPING);
        assertThat(result.description()).isEqualTo("Waste dumped next to the river");
        assertThat(result.photoIds()).containsExactly("photo-1");
    }

    @Test
    void submittedReportStartsWithCorrectInitialState() {
        Reporter reporter = new Reporter("Jane Doe", "jane@example.com", null);

        Report report = useCase.execute(new SubmitReportCommand(
                ReportType.OVERFLOWING_BIN, null,
                GeoLocation.of(48.2082, 16.3738), reporter, null));

        assertThat(report.status()).isEqualTo(ReportStatus.NEW);
        assertThat(report.priority()).isEqualTo(ReportPriority.NORMAL);
        assertThat(report.isOpen()).isTrue();
        assertThat(report.createdAt()).isEqualTo(NOW);
        assertThat(report.lastModifiedAt()).isEqualTo(NOW);
        assertThat(report.reporter()).isEqualTo(reporter);
    }

    @Test
    void missingReporterFallsBackToAnonymous() {
        Report report = useCase.execute(new SubmitReportCommand(
                ReportType.LITTER, null, GeoLocation.of(48.2082, 16.3738), null, null));

        assertThat(report.reporter().isAnonymous()).isTrue();
    }

    @Test
    void missingTypeIsRejectedAndNothingIsSaved() {
        SubmitReportCommand command = new SubmitReportCommand(
                null, null, GeoLocation.of(48.2082, 16.3738), null, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("type is required");
        verifyNoInteractions(repository, outbox);
    }

    @Test
    void missingLocationIsRejectedAndNothingIsSaved() {
        SubmitReportCommand command = new SubmitReportCommand(
                ReportType.LITTER, null, null, null, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("location is required");
        verifyNoInteractions(repository, outbox);
    }

    @Test
    void invalidReporterContactDetailsArePropagated() {
        assertThatThrownBy(() -> useCase.execute(commandWithReporter(
                new Reporter("Jane", "not-an-email", null))))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("email");
        verifyNoInteractions(repository, outbox);
    }

    @Test
    void invalidPhotoReferencesArePropagated() {
        assertThatThrownBy(() -> useCase.execute(commandWithPhotoIds(List.of("", " "))))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("Photo ids");
        verifyNoInteractions(repository, outbox);
    }

    @Test
    void validSubmissionAppendsTheOutboxEntryAfterSuccessfulPersistence() {
        useCase.execute(validCommand());

        InOrder inOrder = inOrder(repository, outbox);
        inOrder.verify(repository).save(any(Report.class));
        inOrder.verify(outbox).append(any(OutboxEntry.class));
    }

    @Test
    void outboxEntryCarriesAReportCreatedEventForThePersistedReport() {
        Report saved = useCase.execute(validCommand());

        OutboxEntry entry = capturedEntry();
        assertThat(entry.eventType()).isEqualTo("report.created");
        assertThat(entry.aggregateType()).isEqualTo("report");
        assertThat(entry.aggregateId()).isEqualTo(saved.id().value());
        assertThat(entry.payload().reportId()).isEqualTo(saved.id().value());
        assertThat(entry.payload().reportType()).isEqualTo(ReportType.ILLEGAL_DUMPING.name());
        assertThat(entry.payload().priority()).isEqualTo(ReportPriority.NORMAL.name());
        assertThat(entry.payload().description()).isEqualTo("Waste dumped next to the river");
    }

    @Test
    void outboxEventIdEqualsTheIntegrationEventIdentity() {
        useCase.execute(validCommand());

        OutboxEntry entry = capturedEntry();
        assertThat(entry.eventId()).isEqualTo(entry.payload().eventId());
        assertThat(entry.occurredAt()).isEqualTo(entry.payload().occurredAt());
    }

    @Test
    void outboxEntryContainsTheReportLocation() {
        GeoLocation location = new GeoLocation(48.2082, 16.3738, "  Main Street 1  ");

        useCase.execute(new SubmitReportCommand(
                ReportType.LITTER, null, location, null, null));

        ReportCreatedEvent.Location publishedLocation = capturedEntry().payload().location();
        assertThat(publishedLocation.latitude()).isEqualTo(location.latitude());
        assertThat(publishedLocation.longitude()).isEqualTo(location.longitude());
        assertThat(publishedLocation.address()).isEqualTo("Main Street 1");
    }

    @Test
    void outboxEntryContainsIdentityAndTimestamps() {
        useCase.execute(validCommand());

        ReportCreatedEvent payload = capturedEntry().payload();
        assertThat(payload.eventId()).isNotNull();
        assertThat(payload.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void correlationIdFromTheLoggingContextIsPreservedInTheOutboxEntry() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CorrelationContext.MDC_KEY, correlationId);

        useCase.execute(validCommand());

        assertThat(capturedEntry().correlationId()).isEqualTo(correlationId);
    }

    @Test
    void withoutACorrelationContextTheOutboxEntryCarriesNone() {
        useCase.execute(validCommand());

        assertThat(capturedEntry().correlationId()).isNull();
    }

    @Test
    void invalidSubmissionsDoNotAppendAnyOutboxEntry() {
        SubmitReportCommand missingType = new SubmitReportCommand(
                null, null, GeoLocation.of(48.2082, 16.3738), null, null);
        SubmitReportCommand missingLocation = new SubmitReportCommand(
                ReportType.LITTER, null, null, null, null);

        assertThatThrownBy(() -> useCase.execute(missingType))
                .isInstanceOf(InvalidReportException.class);
        assertThatThrownBy(() -> useCase.execute(missingLocation))
                .isInstanceOf(InvalidReportException.class);
        assertThatThrownBy(() -> useCase.execute(commandWithReporter(
                new Reporter("Jane", "not-an-email", null))))
                .isInstanceOf(InvalidReportException.class);

        verifyNoInteractions(repository, outbox);
    }

    @Test
    void repositoryFailureDoesNotAppendAnOutboxEntry() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).save(any(Report.class));

        assertThatThrownBy(() -> useCase.execute(validCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");

        verifyNoInteractions(outbox);
    }

    @Test
    void outboxFailureFailsTheSubmissionSoNoPartialStateRemains() {
        doThrow(new IllegalStateException("outbox insert failed"))
                .when(outbox).append(any(OutboxEntry.class));

        // In production both operations run inside one transaction; the unit
        // of work therefore rolls the report back as well (verified by the
        // PostgreSQL integration tests).
        assertThatThrownBy(() -> useCase.execute(validCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox insert failed");

        verify(repository).save(any(Report.class));
    }

    private OutboxEntry capturedEntry() {
        ArgumentCaptor<OutboxEntry> entry = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outbox).append(entry.capture());
        return entry.getValue();
    }

    private SubmitReportCommand validCommand() {
        return new SubmitReportCommand(
                ReportType.ILLEGAL_DUMPING,
                "  Waste dumped next to the river  ",
                GeoLocation.of(48.2082, 16.3738),
                null,
                List.of("photo-1"));
    }

    private SubmitReportCommand commandWithReporter(Reporter reporter) {
        return new SubmitReportCommand(
                ReportType.LITTER, null, GeoLocation.of(48.2082, 16.3738), reporter, null);
    }

    private SubmitReportCommand commandWithPhotoIds(List<String> photoIds) {
        return new SubmitReportCommand(
                ReportType.LITTER, null, GeoLocation.of(48.2082, 16.3738), null, photoIds);
    }
}
