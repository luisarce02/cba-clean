package com.cbclean.incident.infrastructure.persistence.incident;

import com.cbclean.incident.domain.model.Assignment;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentId;
import com.cbclean.incident.domain.model.IncidentLocation;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentStatus;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.domain.model.ReportId;

/**
 * Translates between the {@link Incident} aggregate and its persistence
 * document. Lives in infrastructure; the domain never sees it.
 */
final class IncidentPersistenceMapper {

    private IncidentPersistenceMapper() {
    }

    static IncidentDocument toDocument(Incident incident) {
        return new IncidentDocument(
                incident.id().value().toString(),
                incident.reportId().value().toString(),
                incident.type().name(),
                incident.priority().name(),
                incident.status().name(),
                toDocument(incident.location()),
                incident.description(),
                toDocument(incident.assignment()),
                incident.closingNote(),
                incident.createdAt(),
                incident.lastModifiedAt());
    }

    static Incident toDomain(IncidentDocument document) {
        return Incident.reconstitute(
                new IncidentId(java.util.UUID.fromString(document.getId())),
                new ReportId(java.util.UUID.fromString(document.getReportId())),
                IncidentType.valueOf(document.getType()),
                toDomain(document.getLocation()),
                document.getDescription(),
                IncidentStatus.valueOf(document.getStatus()),
                document.getPriority() == null ? null : IncidentPriority.valueOf(document.getPriority()),
                toDomain(document.getAssignment()),
                document.getClosingNote(),
                document.getCreatedAt(),
                document.getLastModifiedAt());
    }

    private static LocationDocument toDocument(IncidentLocation location) {
        return new LocationDocument(
                location.latitude(),
                location.longitude(),
                location.address(),
                location.zone());
    }

    private static IncidentLocation toDomain(LocationDocument location) {
        return new IncidentLocation(
                location.latitude(),
                location.longitude(),
                location.address(),
                location.zone());
    }

    private static AssignmentDocument toDocument(Assignment assignment) {
        if (assignment == null) {
            return null;
        }
        return new AssignmentDocument(
                assignment.assigneeId(),
                assignment.team(),
                assignment.assignedAt());
    }

    private static Assignment toDomain(AssignmentDocument assignment) {
        if (assignment == null) {
            return null;
        }
        return new Assignment(
                assignment.assigneeId(),
                assignment.team(),
                assignment.assignedAt());
    }
}
