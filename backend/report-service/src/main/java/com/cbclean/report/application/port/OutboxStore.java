package com.cbclean.report.application.port;

import com.cbclean.report.application.outbox.OutboxEntry;

/**
 * Application port for appending integration events to the transactional
 * outbox.
 *
 * <p>The adapter persists the entry in the same database transaction as the
 * aggregate that produced it (the use case enforces this via
 * {@link UnitOfWork}). Publication to the broker happens later, asynchronously,
 * by the outbox publisher - never inside the submission flow.</p>
 */
public interface OutboxStore {

    /**
     * Appends a pending integration event to the outbox.
     *
     * @param entry the event to persist; must be fully populated
     */
    void append(OutboxEntry entry);
}
