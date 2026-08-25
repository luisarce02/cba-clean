package com.cbclean.report.domain.model;

import java.util.UUID;

public record ReportId(UUID value) {

    public ReportId {
        if (value == null) {
            throw new InvalidReportException("Report id must not be null");
        }
    }

    public static ReportId newId() {
        return new ReportId(UUID.randomUUID());
    }

    public static ReportId fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidReportException("Report id must not be blank");
        }
        try {
            return new ReportId(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            throw new InvalidReportException("Report id must be a valid UUID");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
