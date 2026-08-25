package com.cbclean.report.infrastructure.persistence.report;

import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.domain.model.Reporter;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Maps between the domain {@link Report} aggregate and the {@link ReportEntity}
 * persistence model. Value objects are flattened into scalar columns; no
 * persistence concepts leak into the domain.
 */
final class ReportPersistenceMapper {

    private ReportPersistenceMapper() {
    }

    static ReportEntity toEntity(Report report) {
        GeoLocation location = report.location();
        Reporter reporter = report.reporter();
        return new ReportEntity(
                report.id().value(),
                report.type(),
                report.status(),
                report.priority(),
                report.description(),
                report.closingNote(),
                location.latitude(),
                location.longitude(),
                location.address(),
                reporter.name(),
                reporter.email(),
                reporter.phone(),
                new ArrayList<>(report.photoIds()),
                report.createdAt(),
                report.lastModifiedAt());
    }

    static Report toDomain(ReportEntity entity) {
        Reporter reporter;
        if (entity.getReporterName() == null
                && entity.getReporterEmail() == null
                && entity.getReporterPhone() == null) {
            reporter = Reporter.anonymous();
        } else {
            reporter = new Reporter(
                    entity.getReporterName(),
                    entity.getReporterEmail(),
                    entity.getReporterPhone());
        }
        return Report.reconstitute(
                new ReportId(entity.getId()),
                entity.getType(),
                new GeoLocation(entity.getLatitude(), entity.getLongitude(), entity.getAddress()),
                reporter,
                entity.getStatus(),
                entity.getPriority(),
                entity.getDescription(),
                entity.getPhotoIds(),
                entity.getClosingNote(),
                entity.getCreatedAt(),
                entity.getLastModifiedAt());
    }
}
