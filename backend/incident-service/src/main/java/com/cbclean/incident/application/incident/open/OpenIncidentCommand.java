package com.cbclean.incident.application.incident.open;

import com.cbclean.incident.domain.model.IncidentLocation;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.domain.model.ReportId;

/**
 * Input for the "open a new incident" use case.
 *
 * <p>A plain data carrier: all validation lives in the domain. A {@code null}
 * priority lets the domain apply its default.</p>
 */
public record OpenIncidentCommand(
        ReportId reportId,
        IncidentType type,
        IncidentLocation location,
        String description,
        IncidentPriority priority) {
}
