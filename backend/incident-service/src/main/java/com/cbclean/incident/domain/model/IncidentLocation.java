package com.cbclean.incident.domain.model;

import java.util.Objects;

/**
 * Operational location of an incident. Independent of the Report Service's
 * GeoLocation: it adds an optional operational zone used for routing work
 * to field teams, which is a concern of incident handling, not reporting.
 */
public record IncidentLocation(double latitude, double longitude, String address, String zone) {

    private static final int MAX_ADDRESS_LENGTH = 300;
    private static final int MAX_ZONE_LENGTH = 50;

    public IncidentLocation {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new InvalidIncidentException(
                    "Latitude must be between -90 and 90, got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new InvalidIncidentException(
                    "Longitude must be between -180 and 180, got: " + longitude);
        }
        address = normalize(address, MAX_ADDRESS_LENGTH, "Address");
        zone = normalize(zone, MAX_ZONE_LENGTH, "Zone");
    }

    public static IncidentLocation of(double latitude, double longitude) {
        return new IncidentLocation(latitude, longitude, null, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncidentLocation other)) return false;
        return Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0
                && Objects.equals(address, other.address)
                && Objects.equals(zone, other.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, address, zone);
    }

    private static String normalize(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidIncidentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
        return trimmed;
    }
}
