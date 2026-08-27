package com.cbclean.report.infrastructure.persistence.report;

import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.domain.repository.ReportRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PostgreSQL adapter for the domain {@link ReportRepository} port.
 * Translates the domain aggregate into the JPA persistence model and back.
 */
@Repository
public class PostgresReportRepository implements ReportRepository {

    private final ReportJpaRepository jpa;

    public PostgresReportRepository(ReportJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public void save(Report report) {
        jpa.save(ReportPersistenceMapper.toEntity(report));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Report> findById(ReportId id) {
        return jpa.findById(id.value()).map(ReportPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Report> findAll() {
        return jpa.findAll().stream().map(ReportPersistenceMapper::toDomain).collect(Collectors.toList());
    }
}
