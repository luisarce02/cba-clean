package com.cbclean.incident.infrastructure.persistence.processedevent;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persistence record of an integration event claimed for processing.
 *
 * <p>This is messaging bookkeeping state, deliberately kept separate from the
 * {@code IncidentDocument} persistence model and from the domain: incidents
 * carry no knowledge of event identities.</p>
 *
 * <p>The {@code eventId} is the document's {@code @Id}, so MongoDB's native
 * {@code _id} unique index enforces the uniqueness constraint - exactly one
 * record can ever exist per event, regardless of concurrent writers or index
 * creation timing.</p>
 */
@Document(collection = "processed_events")
public class ProcessedEventDocument {

    @Id
    private String eventId;
    private String eventType;
    private Instant processedAt;

    public ProcessedEventDocument() {
    }

    public ProcessedEventDocument(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
