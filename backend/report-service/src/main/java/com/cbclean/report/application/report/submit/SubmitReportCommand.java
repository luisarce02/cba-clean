package com.cbclean.report.application.report.submit;

import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.Reporter;
import com.cbclean.report.domain.model.ReportType;

import java.util.List;

/**
 * Input for the "submit a new waste report" use case.
 *
 * <p>A plain data carrier: all validation lives in the domain. A {@code null}
 * reporter means the report is filed anonymously.</p>
 */
public record SubmitReportCommand(
        ReportType reportType,
        String description,
        GeoLocation location,
        Reporter reporter,
        List<String> photoIds) {
}
