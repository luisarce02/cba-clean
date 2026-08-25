package com.cbclean.incident.integration.event;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID reportId,
        String reportType,
        String priority,
        String description,
        Location location) {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_ADDRESS_LENGTH = 300;

    public ReportCreatedEvent {
        requireValue(eventId, "eventId");
        requireValue(occurredAt, "occurredAt");
        requireValue(reportId, "reportId");
        reportType = requireText(reportType, "reportType");
        priority = requireText(priority, "priority");
        description = normalizeDescription(description);
        requireValue(location, "location");
    }

    public static ReportCreatedEvent of(UUID eventId,
                                        Instant occurredAt,
                                        UUID reportId,
                                        String reportType,
                                        String priority,
                                        String description,
                                        double latitude,
                                        double longitude,
                                        String address) {
        return new ReportCreatedEvent(eventId, occurredAt, reportId, reportType, priority,
                description, new Location(latitude, longitude, address));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(double latitude, double longitude, String address) {

        public Location {
            if (latitude < -90.0 || latitude > 90.0) {
                throw new IllegalArgumentException(
                        "latitude must be between -90 and 90, got: " + latitude);
            }
            if (longitude < -180.0 || longitude > 180.0) {
                throw new IllegalArgumentException(
                        "longitude must be between -180 and 180, got: " + longitude);
            }
            address = normalizeAddress(address);
        }

        private static String normalizeAddress(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.length() > MAX_ADDRESS_LENGTH) {
                throw new IllegalArgumentException(
                        "address must not exceed " + MAX_ADDRESS_LENGTH + " characters");
            }
            return trimmed;
        }
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }
}
