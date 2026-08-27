package com.cbclean.incident.application.incident.get;

import com.cbclean.incident.domain.model.IncidentId;

import java.util.Objects;

public record GetIncidentQuery(IncidentId incidentId) {
    public GetIncidentQuery {
        Objects.requireNonNull(incidentId, "Incident id is required");
    }
}
