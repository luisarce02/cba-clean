package com.cbclean.incident.infrastructure.persistence.incident;

import java.time.Instant;

/**
 * Persistence shape of an incident assignment. Absent when the incident is
 * still unassigned.
 */
public record AssignmentDocument(
        String assigneeId,
        String team,
        Instant assignedAt) {
}
