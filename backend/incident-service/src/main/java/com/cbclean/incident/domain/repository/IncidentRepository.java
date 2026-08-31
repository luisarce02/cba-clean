package com.cbclean.incident.domain.repository;

import com.cbclean.incident.domain.model.DateRange;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentId;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting {@link Incident} aggregates.
 *
 * <p>Owned by the domain because it is expressed purely in domain terms;
 * infrastructure adapters implement it.</p>
 */
public interface IncidentRepository {

    void save(Incident incident);

    Optional<Incident> findById(IncidentId id);

    List<Incident> findAll();

    Page<Incident> findAll(Pageable pageable);

    Page<Incident> findAll(Pageable pageable, DateRange dateRange);
}
