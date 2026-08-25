package com.cbclean.incident.domain.model;

public enum IncidentPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    public boolean isEscalated() {
        return this == HIGH || this == CRITICAL;
    }
}
