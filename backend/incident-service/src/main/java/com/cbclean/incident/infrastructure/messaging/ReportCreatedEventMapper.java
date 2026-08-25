package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.domain.model.IncidentLocation;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.domain.model.ReportId;
import com.cbclean.incident.integration.event.ReportCreatedEvent;

import java.util.UUID;

/**
 * Translates the integration contract ({@link ReportCreatedEvent}) into an
 * {@link OpenIncidentCommand} expressed in Incident Service domain terms.
 *
 * <p>Integration strings are translated explicitly into this service's own
 * domain enums - never by reusing Report Service enums. Unknown values are
 * rejected with {@link EventTranslationException} instead of being silently
 * mapped to arbitrary defaults.</p>
 *
 * <p>Package-private: this mapping is an infrastructure concern of the
 * messaging boundary only.</p>
 */
final class ReportCreatedEventMapper {

    private ReportCreatedEventMapper() {
    }

    static OpenIncidentCommand toCommand(ReportCreatedEvent event) {
        return new OpenIncidentCommand(
                new ReportId(event.reportId()),
                translateType(event.eventId(), event.reportType()),
                new IncidentLocation(
                        event.location().latitude(),
                        event.location().longitude(),
                        event.location().address(),
                        null),
                event.description(),
                translatePriority(event.eventId(), event.priority()));
    }

    private static IncidentType translateType(UUID eventId, String reportType) {
        try {
            return IncidentType.valueOf(reportType.trim());
        } catch (IllegalArgumentException e) {
            throw new EventTranslationException(
                    "Unknown report type '" + reportType + "' in ReportCreatedEvent "
                            + eventId + ": no matching IncidentType");
        }
    }

    private static IncidentPriority translatePriority(UUID eventId, String priority) {
        try {
            return IncidentPriority.valueOf(priority.trim());
        } catch (IllegalArgumentException e) {
            throw new EventTranslationException(
                    "Unknown priority '" + priority + "' in ReportCreatedEvent "
                            + eventId + ": no matching IncidentPriority");
        }
    }
}
