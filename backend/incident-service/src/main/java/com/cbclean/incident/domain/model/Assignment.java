package com.cbclean.incident.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The assignment of an incident to an operational worker, optionally
 * within a team. An incident is initially unassigned.
 */
public record Assignment(String assigneeId, String team, Instant assignedAt) {

    public Assignment {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw new InvalidIncidentException("Assignment requires an assignee id");
        }
        assigneeId = assigneeId.trim();
        team = normalizeTeam(team);
        Objects.requireNonNull(assignedAt, "Assignment time is required");
    }

    public static Assignment to(String assigneeId, Instant assignedAt) {
        return new Assignment(assigneeId, null, assignedAt);
    }

    public static Assignment toTeam(String assigneeId, String team, Instant assignedAt) {
        return new Assignment(assigneeId, team, assignedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Assignment other)) return false;
        return assigneeId.equals(other.assigneeId)
                && Objects.equals(team, other.team)
                && assignedAt.equals(other.assignedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assigneeId, team, assignedAt);
    }

    private static String normalizeTeam(String team) {
        if (team == null || team.isBlank()) {
            return null;
        }
        String trimmed = team.trim();
        if (trimmed.length() > 100) {
            throw new InvalidIncidentException("Team must not exceed 100 characters");
        }
        return trimmed;
    }
}
