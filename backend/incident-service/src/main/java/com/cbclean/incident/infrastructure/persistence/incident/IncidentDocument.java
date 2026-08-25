package com.cbclean.incident.infrastructure.persistence.incident;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persistence model for incidents. This is not the domain aggregate; it only
 * captures the state needed to reconstruct one.
 */
@Document(collection = "incidents")
public class IncidentDocument {

    @Id
    private String id;
    private String reportId;
    private String type;
    private String priority;
    private String status;
    private LocationDocument location;
    private String description;
    private AssignmentDocument assignment;
    private String closingNote;
    private Instant createdAt;
    private Instant lastModifiedAt;

    public IncidentDocument() {
    }

    public IncidentDocument(String id,
                            String reportId,
                            String type,
                            String priority,
                            String status,
                            LocationDocument location,
                            String description,
                            AssignmentDocument assignment,
                            String closingNote,
                            Instant createdAt,
                            Instant lastModifiedAt) {
        this.id = id;
        this.reportId = reportId;
        this.type = type;
        this.priority = priority;
        this.status = status;
        this.location = location;
        this.description = description;
        this.assignment = assignment;
        this.closingNote = closingNote;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }

    public String getId() {
        return id;
    }

    public String getReportId() {
        return reportId;
    }

    public String getType() {
        return type;
    }

    public String getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public LocationDocument getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public AssignmentDocument getAssignment() {
        return assignment;
    }

    public String getClosingNote() {
        return closingNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }
}
