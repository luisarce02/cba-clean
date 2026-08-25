package com.cbclean.report.application.report.get;

import com.cbclean.report.domain.model.ReportId;

import java.util.Objects;

/**
 * Input for the "retrieve a single report" use case.
 *
 * <p>A plain data carrier: parsing and validation of the identifier happen
 * in the domain value object.</p>
 */
public record GetReportQuery(ReportId reportId) {

    public GetReportQuery {
        Objects.requireNonNull(reportId, "Report id is required");
    }
}
