package com.cbclean.incident.application.incident.status;

import com.cbclean.incident.application.incident.get.IncidentNotFoundException;
import com.cbclean.incident.domain.model.Assignment;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentStatus;
import com.cbclean.incident.domain.repository.IncidentRepository;

import java.time.Clock;
import java.util.Objects;

/**
 * Use case for changing incident status following the domain workflow:
 * NEW -> ASSIGNED -> IN_PROGRESS -> RESOLVED, with CANCELLED reachable from any non-terminal.
 * Delegates all transition validation to the {@link Incident} aggregate.
 */
public class UpdateIncidentStatusUseCase {

    private final IncidentRepository incidents;
    private final Clock clock;

    public UpdateIncidentStatusUseCase(IncidentRepository incidents, Clock clock) {
        this.incidents = Objects.requireNonNull(incidents, "Incident repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Incident execute(UpdateIncidentStatusCommand command) {
        Objects.requireNonNull(command, "Update status command is required");
        Incident incident = incidents.findById(command.incidentId())
                .orElseThrow(() -> new IncidentNotFoundException(command.incidentId()));

        IncidentStatus current = incident.status();
        IncidentStatus target = command.targetStatus();

        if (current == target) {
            return incident;
        }

        var now = clock.instant();

        // Allow NEW -> IN_PROGRESS via auto-assign as MVP convenience; otherwise validate via domain
        boolean isNewToInProgress = current == IncidentStatus.NEW && target == IncidentStatus.IN_PROGRESS;
        if (!isNewToInProgress && !current.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal status transition from %s to %s for incident %s".formatted(current, target, command.incidentId()));
        }

        switch (target) {
            case ASSIGNED -> {
                String assignee = command.assigneeId() != null && !command.assigneeId().isBlank()
                        ? command.assigneeId()
                        : "operator";
                incident.assign(Assignment.to(assignee, now), now);
            }
            case IN_PROGRESS -> {
                // If not yet assigned, auto-assign before starting work (MVP convenience)
                if (!incident.isAssigned()) {
                    String assignee = command.assigneeId() != null && !command.assigneeId().isBlank()
                            ? command.assigneeId()
                            : "operator";
                    incident.assign(Assignment.to(assignee, now), now);
                    // After auto-assign, if target is IN_PROGRESS we need to start work
                    // Note: assign already transitioned to ASSIGNED, now transition to IN_PROGRESS
                    incident.startWork(now);
                } else {
                    incident.startWork(now);
                }
            }
            case RESOLVED -> {
                String note = command.closingNote();
                if (note == null || note.isBlank()) {
                    note = "Resolved by operator";
                }
                incident.resolve(note, now);
            }
            case CANCELLED -> {
                String reason = command.closingNote();
                if (reason == null || reason.isBlank()) {
                    reason = "Cancelled by operator";
                }
                incident.cancel(reason, now);
            }
            default -> throw new IllegalStateException("Unsupported status transition to " + target);
        }

        incidents.save(incident);
        return incident;
    }
}
