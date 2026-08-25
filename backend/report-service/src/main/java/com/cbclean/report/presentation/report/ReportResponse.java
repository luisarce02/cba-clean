package com.cbclean.report.presentation.report;

import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.Reporter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP response for a submitted report. Deliberately decoupled from the
 * domain aggregate and from any persistence/JPA representation.
 */
@Schema(description = "A citizen waste report as stored by the service")
public record ReportResponse(
        @Schema(description = "Unique identifier of the report")
        UUID id,
        @Schema(description = "Type of waste problem", allowableValues = {"LITTER", "ILLEGAL_DUMPING", "OVERFLOWING_BIN", "BULKY_WASTE", "OTHER"})
        String type,
        @Schema(description = "Current lifecycle status of the report", allowableValues = {"NEW", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "CANCELLED"})
        String status,
        @Schema(description = "Priority derived from the report type", allowableValues = {"LOW", "NORMAL", "HIGH", "CRITICAL"})
        String priority,
        @Schema(description = "Free-text description of the problem, if provided")
        String description,
        @Schema(description = "Location of the reported problem")
        GeoLocationResponse location,
        @Schema(description = "Reporter contact details; null for anonymous reports")
        ReporterResponse reporter,
        @Schema(description = "Identifiers of photos attached to the report")
        List<String> photoIds,
        @Schema(description = "Timestamp (UTC) when the report was created")
        Instant createdAt,
        @Schema(description = "Timestamp (UTC) when the report was last modified")
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

    @Schema(description = "Geographic location of a report")
    public record GeoLocationResponse(double latitude, double longitude, String address) {

        public static GeoLocationResponse from(GeoLocation location) {
            return new GeoLocationResponse(location.latitude(), location.longitude(), location.address());
        }
    }

    @Schema(description = "Contact details of the reporting citizen; omitted for anonymous reports")
    public record ReporterResponse(String name, String email, String phone) {

        public static ReporterResponse from(Reporter reporter) {
            return reporter.isAnonymous()
                    ? null
                    : new ReporterResponse(reporter.name(), reporter.email(), reporter.phone());
        }
    }
}
