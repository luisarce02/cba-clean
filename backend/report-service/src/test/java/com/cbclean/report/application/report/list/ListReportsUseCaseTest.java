package com.cbclean.report.application.report.list;

import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.repository.ReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListReportsUseCaseTest {

    @Test
    void returnsAllReportsFromRepository() {
        ReportRepository repo = mock(ReportRepository.class);
        Report report = Report.submit(new ReportId(java.util.UUID.randomUUID()), ReportType.LITTER, GeoLocation.of(10, 20), null, "desc", List.of(), null, Instant.now());
        when(repo.findAll()).thenReturn(List.of(report));

        ListReportsUseCase useCase = new ListReportsUseCase(repo);
        List<Report> result = useCase.execute();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(report);
    }

    @Test
    void returnsEmptyWhenNoReports() {
        ReportRepository repo = mock(ReportRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        ListReportsUseCase useCase = new ListReportsUseCase(repo);
        assertThat(useCase.execute()).isEmpty();
    }
}
