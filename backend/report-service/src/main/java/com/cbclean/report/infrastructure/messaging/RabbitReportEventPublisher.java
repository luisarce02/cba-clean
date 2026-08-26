package com.cbclean.report.infrastructure.messaging;

import com.cbclean.report.application.port.ReportEventPublisher;
import com.cbclean.report.integration.event.ReportCreatedEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ adapter for the {@link ReportEventPublisher} port.
 *
 * <p><strong>No longer part of the report submission flow:</strong> since the
 * Transactional Outbox Pattern was introduced, report creation persists its
 * events in the PostgreSQL outbox and the asynchronous outbox publisher
 * delivers them (via {@link RabbitReportEventGateway}). This direct-publish
 * adapter remains for tooling and infrastructure tests; it adds no second wire
 * contract - it only resolves the correlation ID from the SLF4J MDC (where the
 * HTTP correlation filter placed it) and delegates to the gateway.</p>
 */
@Component
public class RabbitReportEventPublisher implements ReportEventPublisher {

    static final String CORRELATION_ID_MDC_KEY = "correlationId";

    public static final String EVENT_TYPE_HEADER = RabbitReportEventGateway.EVENT_TYPE_HEADER;
    public static final String REPORT_CREATED_EVENT_TYPE = RabbitReportEventGateway.REPORT_CREATED_EVENT_TYPE;
    public static final String EVENT_ID_HEADER = RabbitReportEventGateway.EVENT_ID_HEADER;
    public static final String CORRELATION_ID_HEADER = RabbitReportEventGateway.CORRELATION_ID_HEADER;

    private final RabbitReportEventGateway gateway;

    public RabbitReportEventPublisher(RabbitReportEventGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void publishReportCreated(ReportCreatedEvent event) {
        gateway.publish(event, MDC.get(CORRELATION_ID_MDC_KEY));
    }
}
