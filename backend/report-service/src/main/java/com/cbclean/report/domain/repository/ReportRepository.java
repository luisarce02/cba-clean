package com.cbclean.report.domain.repository;

import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting {@link Report} aggregates.
 *
 * <p>Owned by the domain because it is expressed purely in domain terms;
 * infrastructure adapters implement it.</p>
 */
public interface ReportRepository {

    void save(Report report);

    Optional<Report> findById(ReportId id);

    List<Report> findAll();
}
