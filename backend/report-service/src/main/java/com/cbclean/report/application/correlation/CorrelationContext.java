package com.cbclean.report.application.correlation;

import org.slf4j.MDC;

/**
 * Read accessor for the correlation ID of the current unit of work.
 *
 * <p>The correlation ID is placed into the SLF4J MDC by the HTTP correlation
 * filter; domain and application layers stay framework-free apart from the
 * logging API and read it from here when they must hand it to a port (e.g.
 * to store transport/observability metadata in the outbox). It never enters
 * the domain model.</p>
 */
public final class CorrelationContext {

    public static final String MDC_KEY = "correlationId";

    private CorrelationContext() {
    }

    /** @return the current correlation ID, or {@code null} when absent. */
    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
