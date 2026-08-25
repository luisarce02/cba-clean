package com.cbclean.incident.application.incident.open;

import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentId;
import com.cbclean.incident.domain.repository.IncidentRepository;

import java.time.Clock;
import java.util.Objects;

/**
 * Use case: open a new incident originating from a citizen report.
 *
 * <p>Coordinates the flow only - it delegates every business rule to the
 * {@link Incident} aggregate and hands the resulting incident to the repository.</p>
 */
public class OpenIncidentUseCase {

    private final IncidentRepository incidents;
    private final Clock clock;

    public OpenIncidentUseCase(IncidentRepository incidents, Clock clock) {
        this.incidents = Objects.requireNonNull(incidents, "Incident repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Incident execute(OpenIncidentCommand command) {
        Objects.requireNonNull(command, "Open incident command is required");
        Incident incident = Incident.open(
                IncidentId.newId(),
                command.reportId(),
                command.type(),
                command.location(),
                command.description(),
                command.priority(),
                clock.instant());
        incidents.save(incident);
        return incident;
    }
}
