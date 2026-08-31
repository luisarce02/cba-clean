package com.cbclean.incident.infrastructure.persistence.incident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

/**
 * Spring Data Mongo repository used internally by the adapter. Not exposed
 * beyond the persistence package.
 */
public interface IncidentMongoRepository extends MongoRepository<IncidentDocument, String> {

    Page<IncidentDocument> findAllByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    Page<IncidentDocument> findAllByCreatedAtGreaterThanEqual(Instant from, Pageable pageable);

    Page<IncidentDocument> findAllByCreatedAtLessThanEqual(Instant to, Pageable pageable);
}
