package com.cbclean.incident.application.incident.list;

import com.cbclean.incident.domain.model.DateRange;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.repository.IncidentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Objects;

public class GetIncidentsUseCase {

    private final IncidentRepository incidents;

    public GetIncidentsUseCase(IncidentRepository incidents) {
        this.incidents = Objects.requireNonNull(incidents, "Incident repository is required");
    }

    public List<Incident> execute() {
        return incidents.findAll();
    }

    public Page<Incident> execute(Pageable pageable) {
        return incidents.findAll(pageable);
    }

    public Page<Incident> execute(Pageable pageable, DateRange dateRange) {
        return incidents.findAll(pageable, dateRange);
    }
}
