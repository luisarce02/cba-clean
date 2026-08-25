package com.cbclean.report.presentation.report;

import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.model.Reporter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * HTTP request body for submitting a waste report.
 *
 * <p>Carries HTTP-level validation only; the domain remains the final
 * authority for business invariants.</p>
 */
public record SubmitReportRequest(
        @NotNull(message = "reportType is required")
        ReportType reportType,

        @Size(max = 2000, message = "description must not exceed 2000 characters")
        String description,

        @NotNull(message = "location is required")
        @Valid
        GeoLocationRequest location,

        @Valid
        ReporterRequest reporter,

        List<@NotBlank(message = "photoIds must not contain blank values")
             @Size(max = 100, message = "each photoId must not exceed 100 characters") String> photoIds) {

    public SubmitReportCommand toCommand() {
        return new SubmitReportCommand(
                reportType,
                description,
                location.toDomain(),
                reporter == null ? null : reporter.toDomain(),
                photoIds);
    }

    public record GeoLocationRequest(
            @NotNull(message = "latitude is required")
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
            Double latitude,

            @NotNull(message = "longitude is required")
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
            Double longitude,

            @Size(max = 300, message = "address must not exceed 300 characters")
            String address) {

        public GeoLocation toDomain() {
            return new GeoLocation(latitude, longitude, address);
        }
    }

    public record ReporterRequest(
            @Size(max = 100, message = "name must not exceed 100 characters")
            String name,

            @Email(message = "email is not a valid email address")
            @Size(max = 200, message = "email must not exceed 200 characters")
            String email,

            @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "phone is not a valid phone number")
            String phone) {

        public Reporter toDomain() {
            return new Reporter(name, email, phone);
        }
    }
}
