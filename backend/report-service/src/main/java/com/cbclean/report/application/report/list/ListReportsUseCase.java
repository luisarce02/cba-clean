package com.cbclean.report.application.report.list;

import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.repository.ReportRepository;

import java.util.List;
import java.util.Objects;

/**
 * Use case: list all reports. No filtering/pagination for MVP; returns all persisted reports.
 */
public class ListReportsUseCase {

    private final ReportRepository reports;

    public ListReportsUseCase(ReportRepository reports) {
        this.reports = Objects.requireNonNull(reports, "Report repository is required");
    }

    public List<Report> execute() {
        return reports.findAll();
    }
}
