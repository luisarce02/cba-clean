package com.cbclean.incident.application.incident.get;

import com.cbclean.incident.domain.model.IncidentId;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(IncidentId id) {
        super("Incident not found: " + id);
    }
}
