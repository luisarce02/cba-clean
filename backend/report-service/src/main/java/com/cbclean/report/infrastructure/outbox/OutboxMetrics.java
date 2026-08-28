package com.cbclean.report.infrastructure.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for the outbox publisher. Only bounded tags are used;
 * identifiers such as eventId/reportId/correlationId never appear as metric
 * labels - they live in logs via the MDC.
 */
@Component
public class OutboxMetrics {

    public static final String EVENTS_PENDING = "cbaclean.outbox.events.pending";
    public static final String EVENTS_PUBLISHED = "cbaclean.outbox.events.published";
    public static final String EVENTS_PUBLISH_FAILURES = "cbaclean.outbox.events.publish.failures";

    private final MeterRegistry registry;
    private final AtomicLong pending = new AtomicLong();

    public OutboxMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder(EVENTS_PENDING, pending, AtomicLong::get)
                .description("Outbox events currently awaiting publication")
                .register(registry);
        // Eagerly register tagged counters so /actuator/metrics/{name} exists
        // before first publish (otherwise 404 until first event).
        Counter.builder(EVENTS_PUBLISHED)
                .description("Outbox events successfully confirmed by RabbitMQ")
                .tag("eventType", "ReportCreatedEvent")
                .register(registry);
        Counter.builder(EVENTS_PUBLISH_FAILURES)
                .description("Failed outbox publication attempts")
                .tag("eventType", "ReportCreatedEvent")
                .register(registry);
    }

    /** Refreshes the pending gauge after a polling round. */
    void recordPending(long count) {
        pending.set(count);
    }

    void eventPublished(String eventType) {
        Counter.builder(EVENTS_PUBLISHED)
                .description("Outbox events successfully confirmed by RabbitMQ")
                .tag("eventType", eventType)
                .register(registry)
                .increment();
    }

    void eventPublishFailed(String eventType) {
        Counter.builder(EVENTS_PUBLISH_FAILURES)
                .description("Failed outbox publication attempts")
                .tag("eventType", eventType)
                .register(registry)
                .increment();
    }
}
