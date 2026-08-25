package com.cbclean.incident.domain.model;

/**
 * Operational categories used when dispatching waste-management crews.
 * Deliberately distinct from ReportType: the citizen-facing classification
 * and the operational classification serve different purposes.
 */
public enum IncidentType {
    LITTER,
    ILLEGAL_DUMPING,
    OVERFLOWING_BIN,
    BULKY_WASTE,
    MISSED_COLLECTION,
    OTHER;

    public boolean requiresOperationalDetails() {
        return this == ILLEGAL_DUMPING || this == BULKY_WASTE;
    }
}
