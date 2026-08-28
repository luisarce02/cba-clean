package com.cbclean.report.infrastructure.metrics;

import com.cbclean.report.application.port.ReportMetrics;
import com.cbclean.report.domain.model.Report;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Micrometer adapter for the {@link ReportMetrics} application port.
 *
 * <p>Metric naming follows the {@code cbaclean.*} convention so they are easy
 * to find in {@code /actuator/metrics} and {@code /actuator/prometheus} (where
 * dots become underscores, e.g. {@code cbaclean_reports_created}).</p>
 *
 * <p>Cardinality: only bounded tags are used ({@code result},
 * {@code eventType}); identifiers such as reportId/eventId/correlationId are
 * never attached to metrics - they live in logs via the MDC.</p>
 */
@Component
public class MicrometerReportMetrics implements ReportMetrics {

    public static final String REPORTS_CREATED = "cbaclean.reports.created";
    public static final String REPORTS_FAILED = "cbaclean.reports.failed";
    public static final String CREATION_DURATION = "cbaclean.report.creation.duration";

    private final MeterRegistry registry;

    public MicrometerReportMetrics(MeterRegistry registry) {
        this.registry = registry;
        // Eagerly register counters so /actuator/metrics/{name} returns 200 with 0
        // before any reports have been submitted (otherwise actuator returns 404
        // for unregistered meters, which the frontend surfaces as "metrics 404").
        Counter.builder(REPORTS_CREATED)
                .description("Successfully persisted reports")
                .register(registry);
        Counter.builder(REPORTS_FAILED)
                .description("Failed report submissions")
                .register(registry);
        // Pre-register timer variants (success/failure) so the timer exists with 0 count
        Timer.builder(CREATION_DURATION)
                .description("Duration of complete report submission operations")
                .tag("result", "success")
                .register(registry);
        Timer.builder(CREATION_DURATION)
                .description("Duration of complete report submission operations")
                .tag("result", "failure")
                .register(registry);
    }

    @Override
    public Report timeCreation(Supplier<Report> creation) {
        Timer.Sample sample = Timer.start(registry);
        try {
            Report report = creation.get();
            sample.stop(creationTimer("success"));
            return report;
        } catch (RuntimeException e) {
            sample.stop(creationTimer("failure"));
            throw e;
        }
    }

    private Timer creationTimer(String result) {
        return Timer.builder(CREATION_DURATION)
                .description("Duration of complete report submission operations")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void reportCreated() {
        counter(REPORTS_CREATED, "Successfully persisted reports").increment();
    }

    @Override
    public void reportFailed() {
        counter(REPORTS_FAILED, "Failed report submissions").increment();
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name)
                .description(description)
                .register(registry);
    }
}
