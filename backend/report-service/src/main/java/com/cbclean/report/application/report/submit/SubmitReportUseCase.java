package com.cbclean.report.application.report.submit;

import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.domain.repository.ReportRepository;

import java.time.Clock;
import java.util.Objects;

/**
 * Use case: submit a new waste report.
 *
 * <p>Coordinates the flow only - it delegates every business rule to the
 * {@link Report} aggregate and hands the resulting report to the repository.</p>
 */
public class SubmitReportUseCase {

    private final ReportRepository reports;
    private final Clock clock;

    public SubmitReportUseCase(ReportRepository reports, Clock clock) {
        this.reports = Objects.requireNonNull(reports, "Report repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Report execute(SubmitReportCommand command) {
        Objects.requireNonNull(command, "Submission command is required");
        Report report = Report.submit(
                ReportId.newId(),
                command.reportType(),
                command.location(),
                command.reporter(),
                command.description(),
                command.photoIds(),
                null,
                clock.instant());
        reports.save(report);
        return report;
    }
}
