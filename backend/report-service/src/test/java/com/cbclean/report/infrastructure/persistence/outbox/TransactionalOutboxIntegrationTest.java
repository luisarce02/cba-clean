package com.cbclean.report.infrastructure.persistence.outbox;

import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.InvalidReportException;
import com.cbclean.report.domain.model.ReportType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.slf4j.MDC;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Verifies the real transactional behaviour of the outbox against PostgreSQL:
 * report + outbox entry are committed together or not at all, and the claimed
 * event lifecycle works as designed. Transaction semantics are never tested
 * with mocks only.
 */
@SpringBootTest(properties = "cbaclean.outbox.poll-interval=PT1H")
@Testcontainers
class TransactionalOutboxIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SubmitReportUseCase submitReportUseCase;

    @Autowired
    private OutboxJpaRepository outboxJpa;

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private com.cbclean.report.infrastructure.persistence.report.ReportJpaRepository reportJpa;

    @MockitoSpyBean
    private PostgresOutboxStore outboxStore;

    @BeforeEach
    void cleanDatabase() {
        MDC.clear();
        reset(reportJpa, outboxStore);
        outboxJpa.deleteAll();
        reportJpa.deleteAllInBatch();
        reset(reportJpa, outboxStore);
    }

    @Test
    void reportAndOutboxEntryAreCommittedTogether() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        submitReportUseCase.execute(new SubmitReportCommand(
                ReportType.ILLEGAL_DUMPING,
                "Waste dumped next to the river",
                new GeoLocation(48.2082, 16.3738, "Danube riverside"),
                null,
                List.of("photo-1")));

        assertThat(reportJpa.count()).isEqualTo(1);
        assertThat(outboxJpa.count()).isEqualTo(1);

        OutboxEventEntity row = outboxJpa.findAll().iterator().next();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getAggregateType()).isEqualTo("report");
        ReportCreatedEvent payload = objectMapper.readValue(row.getPayload(), ReportCreatedEvent.class);
        assertThat(row.getAggregateId()).isEqualTo(payload.reportId());
        assertThat(row.getEventType()).isEqualTo("report.created");
        assertThat(row.getCorrelationId()).isEqualTo(correlationId);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getCreatedAt()).isNotNull();

        // The outbox id is the integration event identity - downstream
        // idempotency depends on this.
        assertThat(payload.eventId()).isEqualTo(row.getId());
        // PostgreSQL TIMESTAMPTZ stores (rounded) microseconds; compare at
        // that precision.
        assertThat(payload.occurredAt())
                .isCloseTo(row.getOccurredAt(), within(1, ChronoUnit.MICROS));
    }

    @Test
    void payloadRoundTripsIntoAnEqualReportCreatedEvent() throws Exception {
        submitReportUseCase.execute(new SubmitReportCommand(
                ReportType.OVERFLOWING_BIN,
                "Bin overflowing on the corner",
                new GeoLocation(-33.865143, 151.209900, "George Street"),
                null,
                List.of()));

        OutboxEventEntity row = outboxJpa.findAll().iterator().next();
        ReportCreatedEvent deserialized = objectMapper.readValue(
                row.getPayload(), ReportCreatedEvent.class);

        assertThat(deserialized.eventId()).isEqualTo(row.getId());
        assertThat(deserialized.occurredAt())
                .isCloseTo(row.getOccurredAt(), within(1, ChronoUnit.MICROS));
        assertThat(deserialized.reportId()).isEqualTo(row.getAggregateId());
        assertThat(deserialized.reportType()).isEqualTo("OVERFLOWING_BIN");
        assertThat(deserialized.priority()).isEqualTo("NORMAL");
        assertThat(deserialized.description()).isEqualTo("Bin overflowing on the corner");
        assertThat(deserialized.location().latitude()).isEqualTo(-33.865143);
        assertThat(deserialized.location().longitude()).isEqualTo(151.209900);
        assertThat(deserialized.location().address()).isEqualTo("George Street");
    }

    @Test
    void withoutCorrelationContextTheColumnStaysNull() {
        submitReportUseCase.execute(validCommand());

        OutboxEventEntity row = outboxJpa.findAll().iterator().next();
        assertThat(row.getCorrelationId()).isNull();
    }

    @Test
    void reportPersistenceFailureRollsBackTheOutboxEntry() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(reportJpa).save(any());

        assertThatThrownBy(() -> submitReportUseCase.execute(validCommand()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("database unavailable");

        assertThat(reportJpa.count()).isZero();
        assertThat(outboxJpa.count()).isZero();
    }

    @Test
    void outboxFailureRollsBackTheReport() {
        doThrow(new IllegalStateException("outbox insert failed"))
                .when(outboxStore).append(any());

        assertThatThrownBy(() -> submitReportUseCase.execute(validCommand()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("outbox insert failed");

        // Same transaction: neither side of the unit of work survived.
        assertThat(outboxJpa.count()).isZero();
        assertThat(reportJpa.count()).isZero();
    }

    @Test
    void domainValidationStillRejectsInvalidReportsBeforeAnyPersistence() {
        assertThatThrownBy(() -> submitReportUseCase.execute(new SubmitReportCommand(
                ReportType.LITTER, "x".repeat(2001), GeoLocation.of(48.2, 16.3), null, null)))
                .isInstanceOf(InvalidReportException.class);

        assertThat(reportJpa.count()).isZero();
        assertThat(outboxJpa.count()).isZero();
    }

    @Test
    void pendingEventsCanBeClaimedAtomicallyAndAreInvisibleAfterwards() {
        UUID firstId = submitAndReturnEventId();
        UUID secondId = submitAndReturnEventId();

        List<ClaimedOutboxEvent> claimed = outboxEvents.claimPending(10);

        assertThat(claimed).extracting(ClaimedOutboxEvent::id)
                .containsExactly(firstId, secondId);

        for (ClaimedOutboxEvent event : claimed) {
            OutboxEventEntity row = outboxJpa.findById(event.id()).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHING);
            assertThat(row.getAttempts()).isEqualTo(1);
            assertThat(row.getLastAttemptAt()).isNotNull();
        }

        // A concurrent publisher gets nothing while the events are claimed.
        assertThat(outboxEvents.claimPending(10)).isEmpty();
    }

    @Test
    void publishedEventsAreMarkedCorrectlyAndLeaveThePollingSet() {
        UUID eventId = submitAndReturnEventId();
        outboxEvents.claimPending(10);

        Instant publishedAt = Instant.parse("2026-08-25T09:00:00Z");
        outboxEvents.markPublished(eventId, publishedAt);

        OutboxEventEntity row = outboxJpa.findById(eventId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(row.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(outboxEvents.countPending()).isZero();
        assertThat(outboxEvents.claimPending(10)).isEmpty();
    }

    @Test
    void failedPublicationReturnsTheEventToPendingWithBoundedErrorMetadata() {
        UUID eventId = submitAndReturnEventId();
        outboxEvents.claimPending(10);

        outboxEvents.markPublishingFailed(eventId, "broker unavailable");

        OutboxEventEntity row = outboxJpa.findById(eventId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getLastError()).contains("broker unavailable");
        assertThat(outboxEvents.countPending()).isEqualTo(1);

        // Retriable on a later round - attempts keep increasing monotonically.
        ClaimedOutboxEvent retried = outboxEvents.claimPending(10).iterator().next();
        assertThat(retried.id()).isEqualTo(eventId);
        assertThat(retried.attempts()).isEqualTo(2);
    }

    private UUID submitAndReturnEventId() {
        Set<UUID> known = new HashSet<>();
        outboxJpa.findAll().forEach(row -> known.add(row.getId()));
        submitReportUseCase.execute(validCommand());
        return outboxJpa.findAll().stream()
                .map(OutboxEventEntity::getId)
                .filter(id -> !known.contains(id))
                .findFirst()
                .orElseThrow();
    }

    private SubmitReportCommand validCommand() {
        return new SubmitReportCommand(
                ReportType.LITTER,
                "Cigarette butts on the playground",
                GeoLocation.of(48.2082, 16.3738),
                null,
                List.of());
    }
}
