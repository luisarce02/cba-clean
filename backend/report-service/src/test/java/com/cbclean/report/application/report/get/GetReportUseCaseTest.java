package com.cbclean.report.application.report.get;

import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.repository.ReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetReportUseCaseTest {

    private final ReportRepository repository = mock(ReportRepository.class);
    private final GetReportUseCase useCase = new GetReportUseCase(repository);

    @Test
    void existingReportIsReturned() {
        Report report = Report.submit(
                ReportId.newId(),
                ReportType.LITTER,
                GeoLocation.of(48.2082, 16.3738),
                null,
                "Bags of trash",
                List.of("photo-1"),
                null,
                Instant.parse("2026-08-25T10:00:00Z"));
        when(repository.findById(report.id())).thenReturn(Optional.of(report));

        Report result = useCase.execute(new GetReportQuery(report.id()));

        assertThat(result).isSameAs(report);
    }

    @Test
    void unknownIdThrowsReportNotFoundException() {
        ReportId id = ReportId.newId();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetReportQuery(id)))
                .isInstanceOf(ReportNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void missingQueryIsRejected() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void queryRequiresReportId() {
        assertThatThrownBy(() -> new GetReportQuery(null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(repository);
    }
}
