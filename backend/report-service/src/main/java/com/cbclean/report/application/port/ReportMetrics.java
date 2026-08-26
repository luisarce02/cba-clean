package com.cbclean.report.application.port;

import com.cbclean.report.domain.model.Report;

import java.util.function.Supplier;

/**
 * Application-owned metrics port for the report submission flow.
 *
 * <p>Keeps the use case free of any concrete metrics framework: the
 * application only describes <em>what</em> to measure, while an
 * infrastructure adapter (Micrometer) decides <em>how</em>. A no-op
 * implementation is available for tests that do not care about metrics.</p>
 *
 * <p>Metric semantics:</p>
 * <ul>
 *   <li>{@code reportCreated} - after the report and its pending outbox entry
 *   were successfully committed together.</li>
 *   <li>{@code reportFailed} - when submission fails (validation or any
 *   persistence failure inside the unit of work). Event publication outcome is
 *   measured separately by the outbox publisher metrics.</li>
 *   <li>{@code timeCreation} - measures the complete submission operation;
 *   implementations should record success/failure outcomes as bounded,
 *   low-cardinality tags.</li>
 * </ul>
 */
public interface ReportMetrics {

    /**
     * Runs the creation flow, recording its duration (and outcome).
     */
    Report timeCreation(Supplier<Report> creation);

    void reportCreated();

    void reportFailed();

    /** No-op implementation for tests and contexts without a registry. */
    static ReportMetrics noop() {
        return new ReportMetrics() {
            @Override
            public Report timeCreation(Supplier<Report> creation) {
                return creation.get();
            }

            @Override
            public void reportCreated() {
            }

            @Override
            public void reportFailed() {
            }
        };
    }
}
