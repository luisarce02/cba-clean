package com.cbclean.incident.application.incident.get;

import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.repository.IncidentRepository;

import java.util.Objects;

public class GetIncidentUseCase {

    private final IncidentRepository incidents;

    public GetIncidentUseCase(IncidentRepository incidents) {
        this.incidents = Objects.requireNonNull(incidents, "Incident repository is required");
    }

    public Incident execute(GetIncidentQuery query) {
        Objects.requireNonNull(query, "Get incident query is required");
        return incidents.findById(query.incidentId())
                .orElseThrow(() -> new IncidentNotFoundException(query.incidentId()));
    }
}
