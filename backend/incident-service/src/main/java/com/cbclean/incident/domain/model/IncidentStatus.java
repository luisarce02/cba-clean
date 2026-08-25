package com.cbclean.incident.domain.model;

import java.util.Set;

/**
 * Lifecycle of an operational incident:
 *
 *   NEW -> ASSIGNED -> IN_PROGRESS -> RESOLVED
 *
 * CANCELLED is reachable from every non-terminal state, because an
 * operationally open case may become moot at any point before completion
 * (duplicate, false alarm, withdrawn by the city). Terminal states allow
 * no further transitions.
 */
public enum IncidentStatus {
    NEW,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CANCELLED;

    public boolean canTransitionTo(IncidentStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return this == RESOLVED || this == CANCELLED;
    }

    private Set<IncidentStatus> allowedTransitions() {
        return switch (this) {
            case NEW -> Set.of(ASSIGNED, CANCELLED);
            case ASSIGNED -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(RESOLVED, CANCELLED);
            case RESOLVED, CANCELLED -> Set.of();
        };
    }
}
