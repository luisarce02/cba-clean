package com.cbclean.report.application.report.get;

import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.repository.ReportRepository;

import java.util.Objects;

/**
 * Use case: retrieve a single waste report by its identifier.
 *
 * <p>Coordinates the flow only - it loads the {@link Report} aggregate
 * through the repository port and fails with a domain-language exception
 * when the report does not exist.</p>
 */
public class GetReportUseCase {

    private final ReportRepository reports;

    public GetReportUseCase(ReportRepository reports) {
        this.reports = Objects.requireNonNull(reports, "Report repository is required");
    }

    public Report execute(GetReportQuery query) {
        Objects.requireNonNull(query, "Get report query is required");
        return reports.findById(query.reportId())
                .orElseThrow(() -> new ReportNotFoundException(query.reportId()));
    }
}
