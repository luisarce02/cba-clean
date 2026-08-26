package com.cbclean.report.application.report.submit;

import com.cbclean.report.application.port.ReportEventPublisher;
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
 * {@link Report} aggregate, hands the resulting report to the repository and,
 * after successful persistence, publishes a {@link ReportCreatedEvent}
 * through the {@link ReportEventPublisher} port.</p>
 *
 * <p><strong>Failure semantics:</strong> persistence and event publication are
 * deliberately two separate steps and are <em>not</em> atomic. If persistence
 * succeeds but publication fails, the report stays persisted - it is not
 * rolled back artificially (no distributed transaction). Publication is a
 * synchronous best-effort attempt whose failure remains visible to the
 * caller. The Outbox Pattern may later be introduced as a reliability
 * improvement.</p>
 */
public class SubmitReportUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitReportUseCase.class);

    private final ReportRepository reports;
    private final ReportEventPublisher events;
    private final Clock clock;

    public SubmitReportUseCase(ReportRepository reports, ReportEventPublisher events, Clock clock) {
        this.reports = Objects.requireNonNull(reports, "Report repository is required");
        this.events = Objects.requireNonNull(events, "Report event publisher is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Report execute(SubmitReportCommand command) {
        Objects.requireNonNull(command, "Submission command is required");
        Report report = Report.submit(
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
        reports.save(report);
        log.info("operation=report-persisted result=saved reportId={}", report.id().value());

        ReportCreatedEvent event = toEvent(report);
        try {
            events.publishReportCreated(event);
            log.info("operation=event-publish result=published eventType={} eventId={} reportId={}",
                    "report.created", event.eventId(), event.reportId());
        } catch (RuntimeException publicationFailure) {
            log.error("operation=event-publish result=failed eventType={} eventId={} reportId={}",
                    "report.created", event.eventId(), event.reportId(), publicationFailure);
            throw publicationFailure;
        }
        return report;
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
