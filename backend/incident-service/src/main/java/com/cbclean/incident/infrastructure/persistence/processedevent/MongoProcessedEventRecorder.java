package com.cbclean.incident.infrastructure.persistence.processedevent;

import com.cbclean.incident.application.port.ProcessedEventRecorder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * MongoDB adapter for the {@link ProcessedEventRecorder} port.
 *
 * <p>The claim is a single atomic insert into {@code processed_events}, whose
 * {@code _id} is the {@code eventId}. MongoDB rejects a second insert of the
 * same {@code _id} with a duplicate-key error, so exactly one concurrent
 * caller can win the race - no read-then-write check that could interleave.</p>
 */
@Repository
public class MongoProcessedEventRecorder implements ProcessedEventRecorder {

    private final ProcessedEventMongoRepository events;

    public MongoProcessedEventRecorder(ProcessedEventMongoRepository events) {
        this.events = events;
    }

    @Override
    public boolean tryClaim(UUID eventId, String eventType) {
        try {
            events.insert(new ProcessedEventDocument(
                    eventId.toString(), eventType, Instant.now()));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
