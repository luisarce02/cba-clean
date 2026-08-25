package com.cbclean.incident.domain.repository;

import com.cbclean.incident.domain.model.Incident;

/**
 * Port for persisting {@link Incident} aggregates.
 *
 * <p>Owned by the domain because it is expressed purely in domain terms;
 * infrastructure adapters implement it.</p>
 */
public interface IncidentRepository {

    void save(Incident incident);
}
