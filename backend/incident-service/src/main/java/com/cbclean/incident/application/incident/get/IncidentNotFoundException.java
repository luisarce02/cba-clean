package com.cbclean.incident.application.incident.get;

import com.cbclean.incident.domain.model.IncidentId;

public class IncidentNotFoundException extends RuntimeException {

    private final IncidentId id;

    public IncidentNotFoundException(IncidentId id) {
        super("Incident not found: " + id);
        this.id = id;
    }

    public IncidentId getId() {
        return id;
    }
}
