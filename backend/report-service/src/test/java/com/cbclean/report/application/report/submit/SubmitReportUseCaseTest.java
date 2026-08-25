package com.cbclean.report.application.report.submit;

import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.InvalidReportException;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportPriority;
import com.cbclean.report.domain.model.ReportStatus;
import com.cbclean.report.domain.repository.ReportRepository;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.model.Reporter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SubmitReportUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    private final ReportRepository repository = mock(ReportRepository.class);
    private final SubmitReportUseCase useCase =
            new SubmitReportUseCase(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void validSubmissionCreatesSavesAndReturnsReport() {
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
        verifyNoInteractions(repository);
    }

    @Test
    void missingLocationIsRejectedAndNothingIsSaved() {
        SubmitReportCommand command = new SubmitReportCommand(
                ReportType.LITTER, null, null, null, null);

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("location is required");
        verifyNoInteractions(repository);
    }

    @Test
    void invalidReporterContactDetailsArePropagated() {
        assertThatThrownBy(() -> useCase.execute(commandWithReporter(
                new Reporter("Jane", "not-an-email", null))))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("email");
        verifyNoInteractions(repository);
    }

    @Test
    void invalidPhotoReferencesArePropagated() {
        assertThatThrownBy(() -> useCase.execute(commandWithPhotoIds(List.of("", " "))))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("Photo ids");
        verifyNoInteractions(repository);
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
