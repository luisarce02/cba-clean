package com.cbclean.report.infrastructure.persistence.outbox;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Publisher-side operations on the outbox table: atomically claiming pending
 * events, confirming publication and returning failed attempts to the pollable
 * state. Kept deliberately separate from the {@link PostgresOutboxStore}
 * (write side) so each side has a small, explicit surface.
 *
 * <p>Claims use {@code SELECT ... FOR UPDATE SKIP LOCKED} plus the
 * {@code PENDING} &rarr; {@code PUBLISHING} transition, so multiple publisher
 * instances never work on the same event at once.</p>
 */
@Repository
public class OutboxEventRepository {

    private final OutboxJpaRepository jpa;
    private final Clock clock;

    public OutboxEventRepository(OutboxJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    /**
     * Claims up to {@code batchSize} pending events (oldest first), marking
     * them {@code PUBLISHING}. Claimed events are invisible to other publishers
     * until they are either published or returned to {@code PENDING}.
     */
    @Transactional
    public List<ClaimedOutboxEvent> claimPending(int batchSize) {
        Instant attemptAt = clock.instant();
        List<UUID> lockedIds = jpa.lockPendingIds(batchSize);
        List<ClaimedOutboxEvent> claimed = new ArrayList<>(lockedIds.size());
        for (UUID id : lockedIds) {
            OutboxEventEntity entity = jpa.findById(id).orElseThrow(
                    () -> new IllegalStateException("Outbox event " + id + " vanished while claiming"));
            entity.markPublishing(attemptAt);
            claimed.add(ClaimedOutboxEvent.from(entity));
        }
        return claimed;
    }

    /** Marks an event as published - only after RabbitMQ confirmed acceptance. */
    @Transactional
    public void markPublished(UUID id, Instant publishedAt) {
        jpa.findById(id).ifPresent(entity -> entity.markPublished(publishedAt));
    }

    /** Returns a failed attempt to {@code PENDING}, recording bounded error info. */
    @Transactional
    public void markPublishingFailed(UUID id, String error) {
        jpa.findById(id).ifPresent(entity -> entity.markPublishingFailed(error));
    }

    public long countPending() {
        return jpa.countByStatus(OutboxStatus.PENDING);
    }
}
