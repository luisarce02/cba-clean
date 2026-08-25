package com.cbclean.incident.domain.model;

import java.util.UUID;

public record IncidentId(UUID value) {

    public IncidentId {
        if (value == null) {
            throw new InvalidIncidentException("Incident id must not be null");
        }
    }

    public static IncidentId newId() {
        return new IncidentId(UUID.randomUUID());
    }

    public static IncidentId fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIncidentException("Incident id must not be blank");
        }
        try {
            return new IncidentId(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            throw new InvalidIncidentException("Incident id must be a valid UUID");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
