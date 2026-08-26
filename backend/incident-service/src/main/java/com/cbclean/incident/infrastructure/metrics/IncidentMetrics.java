package com.cbclean.incident.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Micrometer instrumentation for the incident consumption flow.
 *
 * <p>Lives entirely at the infrastructure boundary: the application use case
 * and the domain stay metrics-free. Metric naming follows the
 * {@code cbaclean.*} convention (Prometheus rendering:
 * {@code cbaclean_incidents_created_total}, {@code cbaclean_incident_event_processing_duration_seconds}, ...).</p>
 *
 * <p>Cardinality: only bounded tags are used ({@code eventType},
 * {@code result}, {@code reason}); identifiers such as
 * eventId/reportId/incidentId/correlationId are never attached to metrics -
 * they live in logs via the MDC.</p>
 */
@Component
public class IncidentMetrics {

    public static final String INCIDENTS_CREATED = "cbaclean.incidents.created";
    public static final String INCIDENTS_FAILED = "cbaclean.incidents.failed";
    public static final String EVENTS_PROCESSED = "cbaclean.incident.events.processed";
    public static final String EVENTS_DUPLICATES = "cbaclean.incident.events.duplicates";
    public static final String EVENTS_RETRIES = "cbaclean.incident.events.retries";
    public static final String EVENTS_DEAD_LETTERED = "cbaclean.incident.events.dead_lettered";
    public static final String PROCESSING_DURATION = "cbaclean.incident.event.processing.duration";

    /** Bounded reasons for dead-letter routing. */
    public static final String REASON_RETRY_EXHAUSTED = "retry_exhausted";
    public static final String REASON_TRANSLATION_FAILURE = "translation_failure";

    private final MeterRegistry registry;

    public IncidentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Handle for recording the outcome of one event processing attempt.
     * Exactly one of {@link #success(String)}/{@link #failure(String)} should
     * be called per started timer; calling neither records no observation.
     */
    public ProcessingTimer startProcessingTimer() {
        return new ProcessingTimer(Timer.start(registry));
    }

    public class ProcessingTimer {

        private final Timer.Sample sample;

        private ProcessingTimer(Timer.Sample sample) {
            this.sample = sample;
        }

        public void success(String eventType) {
            sample.stop(processingTimer(eventType, "success"));
        }

        public void failure(String eventType) {
            sample.stop(processingTimer(eventType, "failure"));
        }
    }

    private Timer processingTimer(String eventType, String result) {
        return Timer.builder(PROCESSING_DURATION)
                .description("Duration of ReportCreatedEvent processing attempts")
                .tag("eventType", eventType)
                .tag("result", result)
                .register(registry);
    }

    public void incidentCreated() {
        counter(INCIDENTS_CREATED, "Successfully persisted incidents").increment();
    }

    public void incidentFailed() {
        counter(INCIDENTS_FAILED, "Failed incident creations").increment();
    }

    public void eventProcessed(String eventType) {
        Counter.builder(EVENTS_PROCESSED)
                .description("Integration events successfully processed")
                .tag("eventType", eventType)
                .register(registry)
                .increment();
    }

    public void duplicateDetected(String eventType) {
        Counter.builder(EVENTS_DUPLICATES)
                .description("Duplicate events skipped by idempotency")
                .tag("eventType", eventType)
                .register(registry)
                .increment();
    }

    public void retryScheduled(String eventType) {
        Counter.builder(EVENTS_RETRIES)
                .description("Events scheduled for a bounded retry")
                .tag("eventType", eventType)
                .register(registry)
                .increment();
    }

    public void deadLettered(String eventType, String reason) {
        Counter.builder(EVENTS_DEAD_LETTERED)
                .description("Events routed to the dead letter queue")
                .tag("eventType", eventType)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /** No-op variant for tests that do not care about metrics. */
    public static IncidentMetrics noop() {
        return new IncidentMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()) {
            @Override
            public ProcessingTimer startProcessingTimer() {
                return new ProcessingTimer(null) {
                    @Override
                    public void success(String eventType) {
                    }

                    @Override
                    public void failure(String eventType) {
                    }
                };
            }

            @Override
            public void incidentCreated() {
            }

            @Override
            public void incidentFailed() {
            }

            @Override
            public void eventProcessed(String eventType) {
            }

            @Override
            public void duplicateDetected(String eventType) {
            }

            @Override
            public void retryScheduled(String eventType) {
            }

            @Override
            public void deadLettered(String eventType, String reason) {
            }
        };
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name)
                .description(description)
                .register(registry);
    }
}
