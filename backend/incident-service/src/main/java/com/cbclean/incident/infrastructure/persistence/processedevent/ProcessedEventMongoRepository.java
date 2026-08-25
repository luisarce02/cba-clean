package com.cbclean.incident.infrastructure.persistence.processedevent;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data access to the {@code processed_events} collection.
 *
 * <p>Only insert semantics are used by the adapter; reads exist for
 * observability and tests.</p>
 */
public interface ProcessedEventMongoRepository extends MongoRepository<ProcessedEventDocument, String> {
}
