package com.cbclean.incident.infrastructure.messaging;

import com.cbclean.incident.application.incident.open.OpenIncidentCommand;
import com.cbclean.incident.domain.model.IncidentLocation;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportCreatedEventMapperTest {

    private static final UUID EVENT_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T09:30:00Z");
    private static final UUID REPORT_ID = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");

    private ReportCreatedEvent validEvent(String reportType, String priority) {
        return new ReportCreatedEvent(
                EVENT_ID,
                OCCURRED_AT,
                REPORT_ID,
                reportType,
                priority,
                "  Overflowing bin at the market square  ",
                new ReportCreatedEvent.Location(48.2082, 16.3738, "Marktplatz 1"));
    }

    @Test
    void mapsValidEventToCommand() {
        OpenIncidentCommand command = ReportCreatedEventMapper.toCommand(
                validEvent("BULKY_WASTE", "HIGH"));

        assertThat(command.reportId().value()).isEqualTo(REPORT_ID);
        assertThat(command.type()).isEqualTo(IncidentType.BULKY_WASTE);
        assertThat(command.priority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(command.description()).isEqualTo("Overflowing bin at the market square");
        assertThat(command.location()).isEqualTo(
                new IncidentLocation(48.2082, 16.3738, "Marktplatz 1", null));
    }

    @Test
    void mapsReportIdCorrectly() {
        OpenIncidentCommand command = ReportCreatedEventMapper.toCommand(
                validEvent("LITTER", "LOW"));

        assertThat(command.reportId()).isNotNull();
        assertThat(command.reportId().value()).isEqualTo(REPORT_ID);
    }

    @Test
    void mapsEveryReportTypeToItsCounterpartIncidentType() {
        for (String reportType : new String[] {
                "LITTER", "ILLEGAL_DUMPING", "OVERFLOWING_BIN", "BULKY_WASTE", "OTHER"}) {
            OpenIncidentCommand command =
                    ReportCreatedEventMapper.toCommand(validEvent(reportType, "NORMAL"));

            assertThat(command.type())
                    .as("report type %s", reportType)
                    .isEqualTo(IncidentType.valueOf(reportType));
        }
    }

    @Test
    void mapsEveryPriorityToItsCounterpartIncidentPriority() {
        for (String priority : new String[] {"LOW", "NORMAL", "HIGH", "CRITICAL"}) {
            OpenIncidentCommand command =
                    ReportCreatedEventMapper.toCommand(validEvent("LITTER", priority));

            assertThat(command.priority())
                    .as("priority %s", priority)
                    .isEqualTo(IncidentPriority.valueOf(priority));
        }
    }

    @Test
    void mapsLocationWithoutZoneBecauseTheEventCarriesNoZone() {
        OpenIncidentCommand command = ReportCreatedEventMapper.toCommand(
                validEvent("LITTER", "LOW"));

        IncidentLocation location = command.location();
        assertThat(location.latitude()).isEqualTo(48.2082);
        assertThat(location.longitude()).isEqualTo(16.3738);
        assertThat(location.address()).isEqualTo("Marktplatz 1");
        assertThat(location.zone()).isNull();
    }

    @Test
    void preservesDescriptionVerbatimAfterContractNormalization() {
        OpenIncidentCommand command = ReportCreatedEventMapper.toCommand(
                validEvent("LITTER", "LOW"));

        assertThat(command.description()).isEqualTo("Overflowing bin at the market square");
    }

    @Test
    void unknownReportTypeIsRejectedExplicitly() {
        ReportCreatedEvent event = validEvent("ROAD_CRATER", "LOW");

        assertThatThrownBy(() -> ReportCreatedEventMapper.toCommand(event))
                .isInstanceOf(EventTranslationException.class)
                .hasMessageContaining("ROAD_CRATER")
                .hasMessageContaining("IncidentType");
    }

    @Test
    void unknownPriorityIsRejectedExplicitly() {
        ReportCreatedEvent event = validEvent("LITTER", "URGENT");

        assertThatThrownBy(() -> ReportCreatedEventMapper.toCommand(event))
                .isInstanceOf(EventTranslationException.class)
                .hasMessageContaining("URGENT")
                .hasMessageContaining("IncidentPriority");
    }

    @Test
    void lowerCaseIntegrationValuesAreRejectedInsteadOfGuessing() {
        ReportCreatedEvent event = validEvent("litter", "high");

        assertThatThrownBy(() -> ReportCreatedEventMapper.toCommand(event))
                .isInstanceOf(EventTranslationException.class);
    }
}
