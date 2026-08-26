package com.cbclean.report.application.report.submit;

import com.cbclean.report.application.port.ReportEventPublisher;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.InvalidReportException;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.repository.ReportRepository;
import com.cbclean.report.infrastructure.metrics.MicrometerReportMetrics;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Observable metric behaviour of the submission flow against a real
 * (in-memory) Micrometer registry.
 */
class SubmitReportUseCaseMetricsTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerReportMetrics metrics = new MicrometerReportMetrics(registry);
    private final ReportRepository repository = mock(ReportRepository.class);
    private final ReportEventPublisher events = mock(ReportEventPublisher.class);
    private final SubmitReportUseCase useCase =
            new SubmitReportUseCase(repository, events, Clock.fixed(NOW, ZoneOffset.UTC), metrics);

    @BeforeEach
    void resetMocks() {
        Mockito.reset(repository, events);
    }

    private SubmitReportCommand validCommand() {
        return new SubmitReportCommand(
                ReportType.LITTER, "Bags of trash", GeoLocation.of(48.2, 16.3), null, null);
    }

    private double counterValue(String name) {
        // Meters are registered lazily on first use; absent means never incremented.
        return registry.find(name).counters()
                .stream().mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }

    @Test
    void successfulSubmissionIncrementsCreatedAndPublishedCounters() {
        useCase.execute(validCommand());

        assertThat(counterValue(MicrometerReportMetrics.REPORTS_CREATED)).isEqualTo(1.0);
        assertThat(registry.get(MicrometerReportMetrics.EVENTS_PUBLISHED).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(MicrometerReportMetrics.EVENTS_PUBLISHED).counter()
                .getId().getTag("eventType")).isEqualTo("report.created");
        assertThat(counterValue(MicrometerReportMetrics.REPORTS_FAILED)).isZero();
        assertThat(counterValue(MicrometerReportMetrics.EVENTS_PUBLISH_FAILURES)).isZero();
    }

    @Test
    void successfulSubmissionRecordsASuccessfulTimingObservation() {
        useCase.execute(validCommand());

        Timer timer = registry.get(MicrometerReportMetrics.CREATION_DURATION)
                .tags("result", "success").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isGreaterThan(0);
    }

    @Test
    void persistenceFailureIncrementsFailedCounterAndRecordsFailureTiming() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository).save(any());

        assertThatThrownBy(() -> useCase.execute(validCommand()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(counterValue(MicrometerReportMetrics.REPORTS_FAILED)).isEqualTo(1.0);
        assertThat(counterValue(MicrometerReportMetrics.REPORTS_CREATED)).isZero();
        assertThat(counterValue(MicrometerReportMetrics.EVENTS_PUBLISH_FAILURES)).isZero();
        assertThat(registry.get(MicrometerReportMetrics.CREATION_DURATION)
                .tags("result", "failure").timer().count()).isEqualTo(1);
    }

    @Test
    void validationFailureIncrementsFailedCounterWithoutTouchingTheRepository() {
        SubmitReportCommand invalid = new SubmitReportCommand(
                null, null, GeoLocation.of(48.2, 16.3), null, null);

        assertThatThrownBy(() -> useCase.execute(invalid))
                .isInstanceOf(InvalidReportException.class);

        assertThat(counterValue(MicrometerReportMetrics.REPORTS_FAILED)).isEqualTo(1.0);
        assertThat(counterValue(MicrometerReportMetrics.REPORTS_CREATED)).isZero();
    }

    @Test
    void publicationFailureIncrementsPublishFailureCounterButNotReportsFailed() {
        doThrow(new IllegalStateException("broker unavailable"))
                .when(events).publishReportCreated(any());

        assertThatThrownBy(() -> useCase.execute(validCommand()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(counterValue(MicrometerReportMetrics.EVENTS_PUBLISH_FAILURES)).isEqualTo(1.0);
        assertThat(registry.get(MicrometerReportMetrics.EVENTS_PUBLISH_FAILURES).counter()
                .getId().getTag("eventType")).isEqualTo("report.created");
        // The report itself was persisted successfully - not a report failure.
        assertThat(counterValue(MicrometerReportMetrics.REPORTS_CREATED)).isEqualTo(1.0);
        assertThat(counterValue(MicrometerReportMetrics.REPORTS_FAILED)).isZero();
    }

    @Test
    void metricsCarryNoHighCardinalityIdentifiers() {
        useCase.execute(validCommand());

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
