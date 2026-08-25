package com.cbclean.incident.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentStatusTest {

    @Test
    void newCanOnlyBeAssignedOrCancelled() {
        assertThat(transitionsFrom(IncidentStatus.NEW))
                .containsExactlyInAnyOrder(IncidentStatus.ASSIGNED, IncidentStatus.CANCELLED);
    }

    @Test
    void assignedCanStartWorkOrBeCancelled() {
        assertThat(transitionsFrom(IncidentStatus.ASSIGNED))
                .containsExactlyInAnyOrder(IncidentStatus.IN_PROGRESS, IncidentStatus.CANCELLED);
    }

    @Test
    void inProgressCanBeResolvedOrCancelled() {
        assertThat(transitionsFrom(IncidentStatus.IN_PROGRESS))
                .containsExactlyInAnyOrder(IncidentStatus.RESOLVED, IncidentStatus.CANCELLED);
    }

    @Test
    void terminalStatesHaveNoTransitions() {
        assertThat(transitionsFrom(IncidentStatus.RESOLVED)).isEmpty();
        assertThat(transitionsFrom(IncidentStatus.CANCELLED)).isEmpty();
    }

    @Test
    void terminalStatesAreRecognized() {
        assertThat(IncidentStatus.RESOLVED.isTerminal()).isTrue();
        assertThat(IncidentStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(IncidentStatus.NEW.isTerminal()).isFalse();
        assertThat(IncidentStatus.ASSIGNED.isTerminal()).isFalse();
        assertThat(IncidentStatus.IN_PROGRESS.isTerminal()).isFalse();
    }

    private Set<IncidentStatus> transitionsFrom(IncidentStatus status) {
        return Set.of(IncidentStatus.values())
                .stream()
                .filter(target -> status.canTransitionTo(target))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
