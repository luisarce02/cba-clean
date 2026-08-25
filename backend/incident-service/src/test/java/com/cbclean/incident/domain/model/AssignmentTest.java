package com.cbclean.incident.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void assignmentWithoutTeamIsPossible() {
        Assignment assignment = Assignment.to("worker-1", NOW);

        assertThat(assignment.assigneeId()).isEqualTo("worker-1");
        assertThat(assignment.team()).isNull();
        assertThat(assignment.assignedAt()).isEqualTo(NOW);
    }

    @Test
    void teamAssignmentNormalizesTeamName() {
        Assignment assignment = Assignment.toTeam("worker-1", " North Crew ", NOW);

        assertThat(assignment.team()).isEqualTo("North Crew");
    }

    @Test
    void valueEqualityIncludesAssigneeTeamAndTime() {
        Assignment assignment = Assignment.toTeam("worker-1", "North", NOW);

        assertThat(assignment).isEqualTo(Assignment.toTeam(" worker-1 ", "North", NOW));
        assertThat(assignment).isNotEqualTo(Assignment.toTeam("worker-2", "North", NOW));
        assertThat(assignment).isNotEqualTo(Assignment.toTeam("worker-1", "South", NOW));
        assertThat(assignment).isNotEqualTo(Assignment.to("worker-1", NOW.plusSeconds(1)));
    }

    @Test
    void blankAssigneeIdIsRejected() {
        assertThatThrownBy(() -> Assignment.to("  ", NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("assignee id");
    }

    @Test
    void missingAssignmentTimeIsRejected() {
        assertThatThrownBy(() -> Assignment.to("worker-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void oversizedTeamIsRejected() {
        assertThatThrownBy(() -> Assignment.toTeam("worker-1", "t".repeat(101), NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("Team");
    }
}
