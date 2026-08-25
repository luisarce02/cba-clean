package com.cbclean.report.presentation.report;

import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.Reporter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for a submitted report. Deliberately decoupled from the
 * domain aggregate and from any persistence/JPA representation.
 */
public record ReportResponse(
        UUID id,
        String type,
        String status,
        String priority,
        String description,
        GeoLocationResponse location,
        ReporterResponse reporter,
        List<String> photoIds,
        Instant createdAt,
        Instant lastModifiedAt) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.id().value(),
                report.type().name(),
                report.status().name(),
                report.priority().name(),
                report.description(),
                GeoLocationResponse.from(report.location()),
                ReporterResponse.from(report.reporter()),
                List.copyOf(report.photoIds()),
                report.createdAt(),
                report.lastModifiedAt());
    }

    public record GeoLocationResponse(double latitude, double longitude, String address) {

        public static GeoLocationResponse from(GeoLocation location) {
            return new GeoLocationResponse(location.latitude(), location.longitude(), location.address());
        }
    }

    public record ReporterResponse(String name, String email, String phone) {

        public static ReporterResponse from(Reporter reporter) {
            return reporter.isAnonymous()
                    ? null
                    : new ReporterResponse(reporter.name(), reporter.email(), reporter.phone());
        }
    }
}
