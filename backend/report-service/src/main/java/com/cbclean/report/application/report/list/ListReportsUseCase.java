package com.cbclean.report.application.report.list;

import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.repository.ReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Objects;

/**
 * Use case: list reports with optional pagination.
 */
public class ListReportsUseCase {

    private final ReportRepository reports;

    public ListReportsUseCase(ReportRepository reports) {
        this.reports = Objects.requireNonNull(reports, "Report repository is required");
    }

    public List<Report> execute() {
        return reports.findAll();
    }

    public Page<Report> execute(Pageable pageable) {
        return reports.findAll(pageable);
    }
}
