package com.cbclean.report.infrastructure.persistence.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository. Infrastructure-internal; never exposed to
 * application or domain layers.
 */
public interface ReportJpaRepository extends JpaRepository<ReportEntity, UUID> {
}
