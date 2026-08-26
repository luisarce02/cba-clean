package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.application.port.ProcessedEventRecorder;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.infrastructure.metrics.IncidentMetrics;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Observable metric behaviour of the consumption flow against a real
 * (in-memory) Micrometer registry: counters for created/failed/duplicates/
 * processed/retries/dead-lettered and the processing timer.
 */
class IncidentMessagingMetricsTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T12:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IncidentMetrics metrics = new IncidentMetrics(registry);
    private final OpenIncidentUseCase useCase = mock(OpenIncidentUseCase.class);
    private final ProcessedEventRecorder processedEvents = mock(ProcessedEventRecorder.class);

    private ReportCreatedEventConsumer consumer;
    private ReportCreatedEventRetryRouter router;

    @BeforeEach
    void setUp() {
        router = new ReportCreatedEventRetryRouter(
                org.mockito.Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
                defaultRetryProperties(), metrics);
        consumer = new ReportCreatedEventConsumer(useCase, processedEvents, router, metrics);
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(true);
        Incident persisted = mock(Incident.class);
        org.mockito.Mockito.when(persisted.id())
                .thenReturn(new com.cbclean.incident.domain.model.IncidentId(UUID.randomUUID()));
        when(useCase.execute(any(OpenIncidentCommand.class))).thenReturn(persisted);
    }

    private IncidentMessagingRetryProperties defaultRetryProperties() {
        IncidentMessagingRetryProperties properties = new IncidentMessagingRetryProperties();
        properties.setMaxRetries(3);
        properties.setDelays(java.util.List.of(
                java.time.Duration.ofSeconds(2),
                java.time.Duration.ofSeconds(4),
                java.time.Duration.ofSeconds(8)));
        properties.validate();
        return properties;
    }

    private ReportCreatedEvent validEvent() {
        return new ReportCreatedEvent(
                UUID.randomUUID(), OCCURRED_AT, UUID.randomUUID(),
                "LITTER", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));
    }

    private double counterValue(String name) {
        return registry.find(name).counters()
                .stream().mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }

    @Test
    void successfulProcessingIncrementsCreatedAndProcessedCounters() {
        consumer.onReportCreated(validEvent(), null, null);

        assertThat(counterValue(IncidentMetrics.INCIDENTS_CREATED)).isEqualTo(1.0);
        assertThat(counterValue(IncidentMetrics.EVENTS_PROCESSED)).isEqualTo(1.0);
        assertThat(registry.get(IncidentMetrics.EVENTS_PROCESSED).counter()
                .getId().getTag("eventType")).isEqualTo("report.created");
        assertThat(counterValue(IncidentMetrics.INCIDENTS_FAILED)).isZero();
        assertThat(counterValue(IncidentMetrics.EVENTS_DUPLICATES)).isZero();
        assertThat(registry.get(IncidentMetrics.PROCESSING_DURATION)
                .tags("result", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void duplicateDetectionIncrementsTheDuplicatesCounter() {
        when(processedEvents.tryClaim(any(UUID.class), anyString())).thenReturn(false);

        consumer.onReportCreated(validEvent(), null, null);

        assertThat(counterValue(IncidentMetrics.EVENTS_DUPLICATES)).isEqualTo(1.0);
        assertThat(counterValue(IncidentMetrics.EVENTS_PROCESSED)).isZero();
        assertThat(counterValue(IncidentMetrics.INCIDENTS_CREATED)).isZero();
    }

    @Test
    void incidentCreationFailureIncrementsTheFailedCounter() {
        doThrow(new IllegalStateException("mongodb unavailable"))
                .when(useCase).execute(any(OpenIncidentCommand.class));

        consumer.onReportCreated(validEvent(), 0, null);

        assertThat(counterValue(IncidentMetrics.INCIDENTS_FAILED)).isEqualTo(1.0);
        assertThat(counterValue(IncidentMetrics.INCIDENTS_CREATED)).isZero();
        // The failure is routed to the retry chain by the real router.
        assertThat(counterValue(IncidentMetrics.EVENTS_RETRIES)).isEqualTo(1.0);
        assertThat(registry.get(IncidentMetrics.PROCESSING_DURATION)
                .tags("result", "failure").timer().count()).isEqualTo(1);
    }

    @Test
    void scheduledRetriesIncrementTheRetriesCounter() {
        router.retryOrDeadLetter(validEvent(), 0, new IllegalStateException("down"), null);

        assertThat(counterValue(IncidentMetrics.EVENTS_RETRIES)).isEqualTo(1.0);
        assertThat(counterValue(IncidentMetrics.EVENTS_DEAD_LETTERED)).isZero();
    }

    @Test
    void exhaustedRetriesDeadLetterWithBoundedReasonTag() {
        router.retryOrDeadLetter(validEvent(), 3, new IllegalStateException("still failing"), null);

        assertThat(registry.get(IncidentMetrics.EVENTS_DEAD_LETTERED)
                .tags("reason", IncidentMetrics.REASON_RETRY_EXHAUSTED).counter().count())
                .isEqualTo(1.0);
        assertThat(counterValue(IncidentMetrics.EVENTS_RETRIES)).isZero();
    }

    @Test
    void poisonMessagesDeadLetterWithTranslationFailureReasonTag() {
        ReportCreatedEvent poison = new ReportCreatedEvent(
                UUID.randomUUID(), OCCURRED_AT, UUID.randomUUID(),
                "SOMETHING_ELSE", "LOW", null,
                new ReportCreatedEvent.Location(48.2, 16.4, null));

        consumer.onReportCreated(poison, null, null);

        assertThat(registry.get(IncidentMetrics.EVENTS_DEAD_LETTERED)
                .tags("reason", IncidentMetrics.REASON_TRANSLATION_FAILURE).counter().count())
                .isEqualTo(1.0);
        assertThat(counterValue(IncidentMetrics.INCIDENTS_CREATED)).isZero();
    }

    @Test
    void timersRecordObservationsForBothOutcomes() {
        consumer.onReportCreated(validEvent(), null, null);
        doThrow(new IllegalStateException("mongodb unavailable"))
                .when(useCase).execute(any(OpenIncidentCommand.class));
        consumer.onReportCreated(validEvent(), 0, null);

        Timer success = registry.get(IncidentMetrics.PROCESSING_DURATION)
                .tags("result", "success").timer();
        Timer failure = registry.get(IncidentMetrics.PROCESSING_DURATION)
                .tags("result", "failure").timer();

        assertThat(success.count()).isEqualTo(1);
        assertThat(failure.count()).isEqualTo(1);
        assertThat(success.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isGreaterThan(0);
        assertThat(failure.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isGreaterThan(0);
    }

    @Test
    void metricsCarryNoHighCardinalityIdentifiers() {
        consumer.onReportCreated(validEvent(), null, UUID.randomUUID().toString());
        consumer.onReportCreated(validEvent(), null, null);

        Set<String> allowedTagKeys = Set.of("result", "eventType", "reason");
        Pattern identifierPattern = Pattern.compile(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

        for (Meter meter : registry.getMeters()) {
            assertThat(meter.getId().getTags().stream().map(Tag::getKey))
                    .as("meter %s must only carry bounded tag keys", meter.getId().getName())
                    .allMatch(allowedTagKeys::contains);
            meter.getId().getTags().stream().map(Tag::getValue).forEach(value ->
                    assertThat(identifierPattern.matcher(value).matches())
                            .as("meter %s must not carry identifier-like tag values", meter.getId().getName())
                            .isFalse());
        }
    }
}
