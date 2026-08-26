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
 *   <li>{@code reportCreated} - after the report was successfully persisted.</li>
 *   <li>{@code reportFailed} - when submission fails before persistence
 *   (validation, persistence errors). Publication failures are counted
 *   separately via {@code eventPublishFailed}.</li>
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

    void eventPublished(String eventType);

    void eventPublishFailed(String eventType);

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

            @Override
            public void eventPublished(String eventType) {
            }

            @Override
            public void eventPublishFailed(String eventType) {
            }
        };
    }
}
