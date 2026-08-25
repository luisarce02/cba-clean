package com.cbclean.incident.domain.model;

import java.time.Instant;
import java.util.Objects;

public class Incident {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_CLOSING_NOTE_LENGTH = 1000;

    private final IncidentId id;
    private final ReportId reportId;
    private final IncidentType type;
    private final IncidentLocation location;
    private final String description;
    private final Instant createdAt;

    private IncidentStatus status;
    private IncidentPriority priority;
    private Assignment assignment;
    private String closingNote;
    private Instant lastModifiedAt;

    private Incident(IncidentId id,
                     ReportId reportId,
                     IncidentType type,
                     IncidentLocation location,
                     String description,
                     IncidentStatus status,
                     IncidentPriority priority,
                     Assignment assignment,
                     String closingNote,
                     Instant createdAt,
                     Instant lastModifiedAt) {
        this.id = requireValue(id, "Incident id is required");
        this.reportId = requireValue(reportId, "Originating report id is required");
        this.type = requireValue(type, "Incident type is required");
        this.location = requireValue(location, "Incident location is required");
        this.status = requireValue(status, "Incident status is required");
        this.priority = priority == null ? IncidentPriority.NORMAL : priority;
        this.assignment = assignment;
        this.closingNote = normalizeClosingNote(closingNote);
        this.description = normalizeDescription(description);
        this.createdAt = requireValue(createdAt, "Incident creation time is required");
        this.lastModifiedAt = lastModifiedAt == null ? createdAt : lastModifiedAt;
    }

    public static Incident open(IncidentId id,
                                ReportId reportId,
                                IncidentType type,
                                IncidentLocation location,
                                String description,
                                IncidentPriority priority,
                                Instant openedAt) {
        return new Incident(id, reportId, type, location, description,
                IncidentStatus.NEW, priority, null, null, openedAt, openedAt);
    }

    public static Incident reconstitute(IncidentId id,
                                        ReportId reportId,
                                        IncidentType type,
                                        IncidentLocation location,
                                        String description,
                                        IncidentStatus status,
                                        IncidentPriority priority,
                                        Assignment assignment,
                                        String closingNote,
                                        Instant createdAt,
                                        Instant lastModifiedAt) {
        return new Incident(id, reportId, type, location, description,
                status, priority, assignment, closingNote, createdAt, lastModifiedAt);
    }

    public void assign(Assignment newAssignment, Instant at) {
        requireOpen("assign");
        Objects.requireNonNull(newAssignment, "Assignment is required");
        if (status == IncidentStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot assign incident " + id + " while work is in progress");
        }
        this.assignment = newAssignment;
        if (status == IncidentStatus.ASSIGNED) {
            this.lastModifiedAt = at;
        } else {
            transitionTo(IncidentStatus.ASSIGNED, at);
        }
    }

    public void startWork(Instant at) {
        if (assignment == null) {
            throw new IllegalStateException(
                    "Cannot start work on unassigned incident " + id);
        }
        transitionTo(IncidentStatus.IN_PROGRESS, at);
    }

    public void resolve(String resolutionNote, Instant at) {
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new InvalidIncidentException(
                    "An incident cannot be resolved without a resolution note");
        }
        transitionTo(IncidentStatus.RESOLVED, at);
        this.closingNote = truncate(resolutionNote.trim());
        this.lastModifiedAt = at;
    }

    public void cancel(String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidIncidentException(
                    "An incident cannot be cancelled without a reason");
        }
        transitionTo(IncidentStatus.CANCELLED, at);
        this.closingNote = truncate(reason.trim());
        this.lastModifiedAt = at;
    }

    public void changePriority(IncidentPriority newPriority, Instant at) {
        requireOpen("change the priority of");
        requireValue(newPriority, "New priority is required");
        if (newPriority == this.priority) {
            return;
        }
        this.priority = newPriority;
        this.lastModifiedAt = at;
    }

    public boolean isOpen() {
        return !status.isTerminal();
    }

    public boolean isAssigned() {
        return assignment != null;
    }

    public IncidentId id() {
        return id;
    }

    public ReportId reportId() {
        return reportId;
    }

    public IncidentType type() {
        return type;
    }

    public IncidentLocation location() {
        return location;
    }

    public String description() {
        return description;
    }

    public IncidentStatus status() {
        return status;
    }

    public IncidentPriority priority() {
        return priority;
    }

    public Assignment assignment() {
        return assignment;
    }

    public String closingNote() {
        return closingNote;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastModifiedAt() {
        return lastModifiedAt;
    }

    private void transitionTo(IncidentStatus target, Instant at) {
        requireOpen("modify");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal status transition from %s to %s for incident %s"
                            .formatted(status, target, id));
        }
        status = target;
        lastModifiedAt = at;
    }

    private void requireOpen(String action) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot " + action + " a terminal incident " + id
                            + " (status: " + status + ")");
        }
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new InvalidIncidentException(message);
        }
        return value;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidIncidentException(
                    "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String normalizeClosingNote(String closingNote) {
        return closingNote == null ? null : truncate(closingNote.trim());
    }

    private static String truncate(String note) {
        if (note.length() > MAX_CLOSING_NOTE_LENGTH) {
            throw new InvalidIncidentException(
                    "Closing note must not exceed " + MAX_CLOSING_NOTE_LENGTH + " characters");
        }
        return note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Incident other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Incident[%s, type=%s, status=%s, priority=%s]"
                .formatted(id, type, status, priority);
    }
}
