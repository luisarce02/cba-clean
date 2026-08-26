package com.cbclean.report.application.report.submit;

import com.cbclean.report.application.correlation.CorrelationContext;
import com.cbclean.report.application.outbox.OutboxEntry;
import com.cbclean.report.application.port.OutboxStore;
import com.cbclean.report.application.port.ReportMetrics;
import com.cbclean.report.application.port.UnitOfWork;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.domain.repository.ReportRepository;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Use case: submit a new waste report.
 *
 * <p>Coordinates the flow only - it delegates every business rule to the
 * {@link Report} aggregate and then persists the report together with its
 * pending {@link ReportCreatedEvent} inside one atomic unit of work
 * (the Transactional Outbox Pattern). Publication to RabbitMQ is
 * <em>not</em> part of this flow: it happens asynchronously from the outbox,
 * so a submission succeeds whenever PostgreSQL is healthy, even if the broker
 * is down.</p>
 *
 * <p><strong>Consistency guarantee:</strong> after this use case returns
 * successfully, either both the report and its outbox entry are committed or
 * neither is. There is never a committed report without its pending event.</p>
 */
public class SubmitReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitReportUseCase.class);

    private final ReportRepository reports;
    private final OutboxStore outbox;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final ReportMetrics metrics;

    public SubmitReportUseCase(ReportRepository reports, OutboxStore outbox,
                               UnitOfWork unitOfWork, Clock clock, ReportMetrics metrics) {
        this.reports = Objects.requireNonNull(reports, "Report repository is required");
        this.outbox = Objects.requireNonNull(outbox, "Outbox store is required");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "Unit of work is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.metrics = Objects.requireNonNull(metrics, "Report metrics are required");
    }

    public Report execute(SubmitReportCommand command) {
        Objects.requireNonNull(command, "Submission command is required");
        return metrics.timeCreation(() -> {
            Report report;
            try {
                report = Report.submit(
                        ReportId.newId(),
                        command.reportType(),
                        command.location(),
                        command.reporter(),
                        command.description(),
                        command.photoIds(),
                        null,
                        clock.instant());
                log.info("operation=report-received result=accepted reportId={} reportType={}",
                        report.id().value(), report.type());
            } catch (RuntimeException validationFailure) {
                metrics.reportFailed();
                log.error("operation=report-submit result=failed reason=validation", validationFailure);
                throw validationFailure;
            }

            // The event identity (eventId) is decided at submission time and
            // travels unchanged through the outbox into RabbitMQ - downstream
            // idempotency relies on this stability.
            ReportCreatedEvent event = toEvent(report);
            OutboxEntry entry = OutboxEntry.forReportCreated(event, CorrelationContext.current());

            try {
                // Single transaction boundary: report + outbox entry commit
                // together or not at all.
                unitOfWork.execute(() -> {
                    reports.save(report);
                    outbox.append(entry);
                    return null;
                });
            } catch (RuntimeException persistenceFailure) {
                metrics.reportFailed();
                log.error("operation=report-submit result=failed reason=persistence reportId={} eventId={}",
                        report.id().value(), event.eventId(), persistenceFailure);
                throw persistenceFailure;
            }

            log.info("operation=report-persisted result=saved reportId={} eventId={}",
                    report.id().value(), event.eventId());
            metrics.reportCreated();
            return report;
        });
    }

    /**
     * Maps the domain aggregate to the integration contract. Domain enums are
     * converted explicitly to their string representations so that the
     * integration event never leaks domain types.
     */
    private ReportCreatedEvent toEvent(Report report) {
        GeoLocation location = report.location();
        return new ReportCreatedEvent(
                UUID.randomUUID(),
                report.createdAt(),
                report.id().value(),
                report.type().name(),
                report.priority().name(),
                report.description(),
                new ReportCreatedEvent.Location(
                        location.latitude(), location.longitude(), location.address()));
    }
}
