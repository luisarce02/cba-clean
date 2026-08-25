package com.cbclean.report.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Report {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final ReportId id;
    private final ReportType type;
    private final GeoLocation location;
    private final Reporter reporter;
    private final Instant createdAt;
    private final List<String> photoIds;

    private ReportStatus status;
    private ReportPriority priority;
    private String description;
    private String closingNote;
    private Instant lastModifiedAt;

    private Report(ReportId id,
                   ReportType type,
                   GeoLocation location,
                   Reporter reporter,
                   ReportStatus status,
                   ReportPriority priority,
                   String description,
                   List<String> photoIds,
                   String closingNote,
                   Instant createdAt,
                   Instant lastModifiedAt) {
        this.id = requireValue(id, "Report id is required");
        this.type = requireValue(type, "Report type is required");
        this.location = requireValue(location, "Report location is required");
        this.reporter = reporter == null ? Reporter.anonymous() : reporter;
        this.status = requireValue(status, "Report status is required");
        this.priority = priority == null ? ReportPriority.NORMAL : priority;
        this.description = normalizeDescription(description);
        this.photoIds = normalizePhotoIds(photoIds);
        this.closingNote = closingNote;
        this.createdAt = requireValue(createdAt, "Report creation time is required");
        this.lastModifiedAt = lastModifiedAt == null ? createdAt : lastModifiedAt;
    }

    public static Report submit(ReportId id,
                                ReportType type,
                                GeoLocation location,
                                Reporter reporter,
                                String description,
                                List<String> photoIds,
                                ReportPriority priority,
                                Instant submittedAt) {
        return new Report(id, type, location, reporter, ReportStatus.NEW, priority,
                description, photoIds, null, submittedAt, submittedAt);
    }

    public static Report reconstitute(ReportId id,
                                      ReportType type,
                                      GeoLocation location,
                                      Reporter reporter,
                                      ReportStatus status,
                                      ReportPriority priority,
                                      String description,
                                      List<String> photoIds,
                                      String closingNote,
                                      Instant createdAt,
                                      Instant lastModifiedAt) {
        return new Report(id, type, location, reporter, status, priority,
                description, photoIds, closingNote, createdAt, lastModifiedAt);
    }

    public void acknowledge(Instant at) {
        transitionTo(ReportStatus.ACKNOWLEDGED, at);
    }

    public void startProcessing(Instant at) {
        transitionTo(ReportStatus.IN_PROGRESS, at);
    }

    public void resolve(String resolutionNote, Instant at) {
        if (resolutionNote == null || resolutionNote.isBlank()) {
            throw new InvalidReportException("A report cannot be resolved without a resolution note");
        }
        transitionTo(ReportStatus.RESOLVED, at);
        this.closingNote = resolutionNote.trim();
        this.lastModifiedAt = at;
    }

    public void cancel(String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidReportException("A report cannot be cancelled without a reason");
        }
        transitionTo(ReportStatus.CANCELLED, at);
        this.closingNote = reason.trim();
        this.lastModifiedAt = at;
    }

    public void changePriority(ReportPriority newPriority, Instant at) {
        requireOpen("change the priority of");
        Objects.requireNonNull(newPriority, "New priority is required");
        if (newPriority == this.priority) {
            return;
        }
        this.priority = newPriority;
        this.lastModifiedAt = at;
    }

    public boolean isOpen() {
        return !status.isClosed();
    }

    public ReportId id() {
        return id;
    }

    public ReportType type() {
        return type;
    }

    public GeoLocation location() {
        return location;
    }

    public Reporter reporter() {
        return reporter;
    }

    public ReportStatus status() {
        return status;
    }

    public ReportPriority priority() {
        return priority;
    }

    public String description() {
        return description;
    }

    public List<String> photoIds() {
        return Collections.unmodifiableList(photoIds);
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

    private void transitionTo(ReportStatus target, Instant at) {
        requireOpen("modify");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal status transition from %s to %s for report %s"
                            .formatted(status, target, id));
        }
        status = target;
        lastModifiedAt = at;
    }

    private void requireOpen(String action) {
        if (status.isClosed()) {
            throw new IllegalStateException(
                    "Cannot " + action + " a closed report " + id + " (status: " + status + ")");
        }
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new InvalidReportException(message);
        }
        return value;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidReportException(
                    "Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return trimmed;
    }

    private static List<String> normalizePhotoIds(List<String> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = photoIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        if (normalized.size() != photoIds.stream().filter(Objects::nonNull).count()) {
            throw new InvalidReportException("Photo ids must be non-blank values");
        }
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new InvalidReportException("Photo ids must be unique");
        }
        return new ArrayList<>(normalized);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Report other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Report[%s, type=%s, status=%s, priority=%s]".formatted(id, type, status, priority);
    }
}
