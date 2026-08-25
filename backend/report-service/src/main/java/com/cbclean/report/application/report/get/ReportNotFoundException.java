package com.cbclean.report.application.report.get;

import com.cbclean.report.domain.model.ReportId;

/**
 * Signals that no report exists for the requested identifier. Application
 * level so the presentation layer can translate it into a 404 response
 * without any infrastructure concept leaking through.
 */
public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException(ReportId id) {
        super("Report not found: " + id);
    }
}
