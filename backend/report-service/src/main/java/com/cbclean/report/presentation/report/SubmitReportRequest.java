package com.cbclean.report.presentation.report;

import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.model.Reporter;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request body for submitting a new citizen waste report")
public record SubmitReportRequest(
        @Schema(description = "Type of waste problem being reported", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.reportType.required}")
        ReportType reportType,

        @Schema(description = "Optional free-text description of the problem (max 2000 characters)", example = "Large pile of household waste dumped next to the park entrance")
        @Size(max = 2000, message = "{validation.description.maxLength}")
        String description,

        @Schema(description = "Location of the reported problem", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.location.required}")
        @Valid
        GeoLocationRequest location,

        @Schema(description = "Optional reporter contact details. Omit to submit anonymously.")
        @Valid
        ReporterRequest reporter,

        @Schema(description = "Optional list of identifiers of photos attached to the report")
        List<@NotBlank(message = "{validation.photoIds.notBlank}")
             @Size(max = 100, message = "{validation.photoId.maxLength}") String> photoIds) {

    public SubmitReportCommand toCommand() {
        return new SubmitReportCommand(
                reportType,
                description,
                location.toDomain(),
                reporter == null ? null : reporter.toDomain(),
                photoIds);
    }

    @Schema(description = "Geographic location of a report. Latitude and longitude are mandatory; address is optional.")
    public record GeoLocationRequest(
            @Schema(description = "Latitude in decimal degrees (-90 to 90)", example = "48.20849", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "{validation.latitude.required}")
            @DecimalMin(value = "-90.0", message = "{validation.latitude.range}")
            @DecimalMax(value = "90.0", message = "{validation.latitude.range}")
            Double latitude,

            @Schema(description = "Longitude in decimal degrees (-180 to 180)", example = "16.37208", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "{validation.longitude.required}")
            @DecimalMin(value = "-180.0", message = "{validation.longitude.range}")
            @DecimalMax(value = "180.0", message = "{validation.longitude.range}")
            Double longitude,

            @Schema(description = "Optional human-readable address (max 300 characters)", example = "Rathausplatz 1, 1010 Vienna")
            @Size(max = 300, message = "{validation.address.maxLength}")
            String address) {

        public GeoLocation toDomain() {
            return new GeoLocation(latitude, longitude, address);
        }
    }

    @Schema(description = "Optional contact details of the reporting citizen")
    public record ReporterRequest(
            @Schema(description = "Reporter's name (max 100 characters)", example = "Jane Doe")
            @Size(max = 100, message = "{validation.reporterName.maxLength}")
            String name,

            @Schema(description = "Reporter's email address", example = "jane.doe@example.com")
            @Email(message = "{validation.reporterEmail.invalid}")
            @Size(max = 200, message = "{validation.reporterEmail.maxLength}")
            String email,

            @Schema(description = "Reporter's phone number, optionally starting with '+', digits and spaces only (6-20 characters)", example = "+43 1 2345678")
            @Pattern(regexp = "^\\+?[0-9 ]{6,20}$", message = "{validation.reporterPhone.invalid}")
            String phone) {

        public Reporter toDomain() {
            return new Reporter(name, email, phone);
        }
    }
}
