package com.cbclean.incident.application.incident.status;

import com.cbclean.incident.domain.model.IncidentId;
import com.cbclean.incident.domain.model.IncidentStatus;

import java.util.Objects;

public record UpdateIncidentStatusCommand(
        IncidentId incidentId,
        IncidentStatus targetStatus,
        String closingNote,
        String assigneeId) {

    public UpdateIncidentStatusCommand {
        Objects.requireNonNull(incidentId, "Incident id is required");
        Objects.requireNonNull(targetStatus, "Target status is required");
    }
}
