package com.cbclean.incident.domain.model;

import java.util.UUID;

/**
 * Opaque reference to the identifier of the citizen report this incident
 * originated from. It carries no knowledge of the Report Service domain;
 * it only guarantees that some report identity value is present.
 */
public record ReportId(UUID value) {

    public ReportId {
        if (value == null) {
            throw new InvalidIncidentException("Report id must not be null");
        }
    }

    public static ReportId fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIncidentException("Report id must not be blank");
        }
        try {
            return new ReportId(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            throw new InvalidIncidentException("Report id must be a valid UUID");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
