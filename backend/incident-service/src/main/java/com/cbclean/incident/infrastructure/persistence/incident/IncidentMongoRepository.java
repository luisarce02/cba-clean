package com.cbclean.incident.infrastructure.persistence.incident;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data Mongo repository used internally by the adapter. Not exposed
 * beyond the persistence package.
 */
public interface IncidentMongoRepository extends MongoRepository<IncidentDocument, String> {
}
