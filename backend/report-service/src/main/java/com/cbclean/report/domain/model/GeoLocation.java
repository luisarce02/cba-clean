package com.cbclean.report.domain.model;

import java.util.Objects;

public record GeoLocation(double latitude, double longitude, String address) {

    public GeoLocation {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new InvalidReportException("Latitude must be between -90 and 90, got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new InvalidReportException("Longitude must be between -180 and 180, got: " + longitude);
        }
        address = normalizeAddress(address);
    }

    public static GeoLocation of(double latitude, double longitude) {
        return new GeoLocation(latitude, longitude, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeoLocation other)) return false;
        return Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0
                && Objects.equals(address, other.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, address);
    }

    private static String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String trimmed = address.trim();
        if (trimmed.length() > 300) {
            throw new InvalidReportException("Address must not exceed 300 characters");
        }
        return trimmed;
    }
}
