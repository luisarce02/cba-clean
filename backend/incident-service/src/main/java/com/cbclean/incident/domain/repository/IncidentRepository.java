package com.cbclean.incident.domain.repository;

import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentId;

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
}
