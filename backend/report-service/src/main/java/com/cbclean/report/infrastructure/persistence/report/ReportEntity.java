package com.cbclean.report.infrastructure.persistence.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.cbclean.report.domain.model.ReportPriority;
import com.cbclean.report.domain.model.ReportStatus;
import com.cbclean.report.domain.model.ReportType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence model for {@code Report}. Contains only persistence concerns;
 * the domain aggregate is never annotated or reused here.
 */
@Entity
@Table(name = "reports")
public class ReportEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ReportType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private ReportPriority priority;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "closing_note", columnDefinition = "text")
    private String closingNote;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "reporter_name", length = 100)
    private String reporterName;

    @Column(name = "reporter_email", length = 200)
    private String reporterEmail;

    @Column(name = "reporter_phone", length = 25)
    private String reporterPhone;

    @Column(name = "photo_ids", nullable = false, columnDefinition = "text[]")
    private List<String> photoIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    protected ReportEntity() {
    }

    ReportEntity(UUID id,
                 ReportType type,
                 ReportStatus status,
                 ReportPriority priority,
                 String description,
                 String closingNote,
                 double latitude,
                 double longitude,
                 String address,
                 String reporterName,
                 String reporterEmail,
                 String reporterPhone,
                 List<String> photoIds,
                 Instant createdAt,
                 Instant lastModifiedAt) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.priority = priority;
        this.description = description;
        this.closingNote = closingNote;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.reporterName = reporterName;
        this.reporterEmail = reporterEmail;
        this.reporterPhone = reporterPhone;
        this.photoIds = photoIds;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }

    public UUID getId() {
        return id;
    }

    public ReportType getType() {
        return type;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public ReportPriority getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public String getClosingNote() {
        return closingNote;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public String getReporterName() {
        return reporterName;
    }

    public String getReporterEmail() {
        return reporterEmail;
    }

    public String getReporterPhone() {
        return reporterPhone;
    }

    public List<String> getPhotoIds() {
        return photoIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastModifiedAt() {
        return lastModifiedAt;
    }
}
