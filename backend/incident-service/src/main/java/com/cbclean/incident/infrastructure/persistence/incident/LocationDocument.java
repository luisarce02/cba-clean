package com.cbclean.incident.infrastructure.persistence.incident;

/**
 * Persistence shape of an incident location: plain coordinates and optional
 * routing information, deliberately free of domain behaviour.
 */
public record LocationDocument(
        double latitude,
        double longitude,
        String address,
        String zone) {
}
