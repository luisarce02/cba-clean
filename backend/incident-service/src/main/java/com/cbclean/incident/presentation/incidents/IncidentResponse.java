package com.cbclean.incident.presentation.incidents;

import com.cbclean.incident.domain.model.Incident;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "An operational incident derived from a citizen report")
public record IncidentResponse(
        @Schema(description = "Unique identifier of the incident")
        UUID id,
        @Schema(description = "Identifier of the originating report")
        UUID reportId,
        @Schema(description = "Operational incident type", allowableValues = {"LITTER","ILLEGAL_DUMPING","OVERFLOWING_BIN","BULKY_WASTE","MISSED_COLLECTION","OTHER"})
        String type,
        @Schema(description = "Current lifecycle status", allowableValues = {"NEW","ASSIGNED","IN_PROGRESS","RESOLVED","CANCELLED"})
        String status,
        @Schema(description = "Operational priority", allowableValues = {"LOW","NORMAL","HIGH","CRITICAL"})
        String priority,
        @Schema(description = "Free-text description, if provided")
        String description,
        @Schema(description = "Location of the incident")
        LocationResponse location,
        @Schema(description = "Assignment; null if not yet assigned")
        AssignmentResponse assignment,
        @Schema(description = "Closing note for RESOLVED/CANCELLED; null otherwise")
        String closingNote,
        @Schema(description = "Timestamp when incident was created")
        Instant createdAt,
        @Schema(description = "Timestamp when incident was last modified")
        Instant lastModifiedAt) {

    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.id().value(),
                incident.reportId().value(),
                incident.type().name(),
                incident.status().name(),
                incident.priority().name(),
                incident.description(),
                LocationResponse.from(incident.location()),
                AssignmentResponse.from(incident.assignment()),
                incident.closingNote(),
                incident.createdAt(),
                incident.lastModifiedAt());
    }

    @Schema(description = "Geographic location of an incident")
    public record LocationResponse(double latitude, double longitude, String address, String zone) {
        public static LocationResponse from(com.cbclean.incident.domain.model.IncidentLocation location) {
            return new LocationResponse(location.latitude(), location.longitude(), location.address(), location.zone());
        }
    }

    @Schema(description = "Assignment of an incident to a worker")
    public record AssignmentResponse(String assigneeId, String team, Instant assignedAt) {
        public static AssignmentResponse from(com.cbclean.incident.domain.model.Assignment assignment) {
            if (assignment == null) return null;
            return new AssignmentResponse(assignment.assigneeId(), assignment.team(), assignment.assignedAt());
        }
    }
}
