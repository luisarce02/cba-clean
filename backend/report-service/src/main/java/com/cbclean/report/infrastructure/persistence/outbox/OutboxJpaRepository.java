package com.cbclean.report.infrastructure.persistence.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for the outbox table.
 */
public interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    long countByStatus(OutboxStatus status);

    /**
     * Locks up to {@code limit} pending events oldest-first so that concurrent
     * publisher instances never claim the same row ({@code SKIP LOCKED});
     * served by the partial index on pending events.
     */
    @Query(value = """
            SELECT id FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> lockPendingIds(@Param("limit") int limit);
}
