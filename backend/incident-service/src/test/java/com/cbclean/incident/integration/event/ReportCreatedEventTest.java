package com.cbclean.incident.integration.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cbclean.incident.integration.event.ReportCreatedEvent.Location;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class ReportCreatedEventTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-25T10:15:30Z");
    private static final UUID REPORT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    @Test
    void canBeConstructedWithValidData() {
        ReportCreatedEvent event = anEvent();

        assertThat(event.eventId()).isEqualTo(EVENT_ID);
        assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.reportId()).isEqualTo(REPORT_ID);
        assertThat(event.reportType()).isEqualTo("LITTER");
        assertThat(event.priority()).isEqualTo("HIGH");
        assertThat(event.description()).isEqualTo("Overflowing bin at the market square");
        assertThat(event.location().latitude()).isEqualTo(48.2081);
        assertThat(event.location().longitude()).isEqualTo(16.3738);
        assertThat(event.location().address()).isEqualTo("Market Square 1");
    }

    @Test
    void descriptionIsOptional() {
        ReportCreatedEvent event = new ReportCreatedEvent(EVENT_ID, OCCURRED_AT, REPORT_ID,
                "LITTER", "HIGH", null,
                new Location(48.2081, 16.3738, null));

        assertThat(event.description()).isNull();
        assertThat(event.location().address()).isNull();
    }

    @Test
    void isImmutable() {
        assertNoSetters(ReportCreatedEvent.class);
        assertNoSetters(Location.class);
        assertThat(allFieldsAreFinal(ReportCreatedEvent.class)).isTrue();
        assertThat(allFieldsAreFinal(Location.class)).isTrue();
    }

    @Test
    void equalEventsHaveSameValueAndHash() {
        ReportCreatedEvent first = anEvent();
        ReportCreatedEvent second = anEvent();

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    void serializesToJsonWithIsoTimestampsAndUuidStrings() throws Exception {
        String json = objectMapper.writeValueAsString(anEvent());

        assertThat(json).contains("\"eventId\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(json).contains("\"reportId\":\"22222222-2222-2222-2222-222222222222\"");
        assertThat(json).contains("\"occurredAt\":\"2026-08-25T10:15:30Z\"");
        assertThat(json).contains("\"reportType\":\"LITTER\"");
        assertThat(json).contains("\"priority\":\"HIGH\"");
        assertThat(json).contains("\"latitude\":48.2081");
    }

    @Test
    void deserializationReconstructsEquivalentEvent() throws Exception {
        String json = objectMapper.writeValueAsString(anEvent());

        ReportCreatedEvent deserialized = objectMapper.readValue(json, ReportCreatedEvent.class);

        assertThat(deserialized).isEqualTo(anEvent());
        assertThat(deserialized.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    void unknownPropertiesAreIgnoredOnDeserialization() throws Exception {
        String json = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "occurredAt": "2026-08-25T10:15:30Z",
                  "reportId": "22222222-2222-2222-2222-222222222222",
                  "reportType": "LITTER",
                  "priority": "HIGH",
                  "description": "Overflowing bin at the market square",
                  "location": {"latitude": 48.2081, "longitude": 16.3738, "address": "Market Square 1"},
                  "futureField": "some-new-value"
                }
                """;

        ReportCreatedEvent deserialized = objectMapper.readValue(json, ReportCreatedEvent.class);

        assertThat(deserialized).isEqualTo(anEvent());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "eventId", "occurredAt", "reportId", "reportType", "priority", "location"
    })
    void missingRequiredFieldIsRejectedOnDeserialization(String missingField) throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(fullJson());
        node.remove(missingField);
        String json = objectMapper.writeValueAsString(node);

        assertThatThrownBy(() -> objectMapper.readValue(json, ReportCreatedEvent.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    class LocationValidation {

        @Test
        void rejectsLatitudeOutOfRange() {
            assertThatThrownBy(() -> new Location(90.5, 0.0, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("latitude");
        }

        @Test
        void rejectsLongitudeOutOfRange() {
            assertThatThrownBy(() -> new Location(0.0, -180.1, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("longitude");
        }
    }

    @Nested
    class RequiredFieldValidation {

        @Test
        void rejectsMissingEventId() {
            assertThatThrownBy(() -> new ReportCreatedEvent(null, OCCURRED_AT, REPORT_ID,
                    "LITTER", "HIGH", null, aLocation()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("eventId");
        }

        @Test
        void rejectsMissingOccurredAt() {
            assertThatThrownBy(() -> new ReportCreatedEvent(EVENT_ID, null, REPORT_ID,
                    "LITTER", "HIGH", null, aLocation()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("occurredAt");
        }

        @Test
        void rejectsMissingReportId() {
            assertThatThrownBy(() -> new ReportCreatedEvent(EVENT_ID, OCCURRED_AT, null,
                    "LITTER", "HIGH", null, aLocation()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reportId");
        }

        @Test
        void rejectsBlankReportType() {
            assertThatThrownBy(() -> new ReportCreatedEvent(EVENT_ID, OCCURRED_AT, REPORT_ID,
                    "  ", "HIGH", null, aLocation()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reportType");
        }

        @Test
        void rejectsBlankPriority() {
            assertThatThrownBy(() -> new ReportCreatedEvent(EVENT_ID, OCCURRED_AT, REPORT_ID,
                    "LITTER", "", null, aLocation()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("priority");
        }

        @Test
        void rejectsMissingLocation() {
            assertThatThrownBy(() -> new ReportCreatedEvent(EVENT_ID, OCCURRED_AT, REPORT_ID,
                    "LITTER", "HIGH", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("location");
        }
    }

    private static ReportCreatedEvent anEvent() {
        return new ReportCreatedEvent(EVENT_ID, OCCURRED_AT, REPORT_ID,
                "LITTER", "HIGH", "Overflowing bin at the market square",
                new Location(48.2081, 16.3738, "Market Square 1"));
    }

    private static Location aLocation() {
        return new Location(48.2081, 16.3738, "Market Square 1");
    }

    private static String fullJson() {
        return """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "occurredAt": "2026-08-25T10:15:30Z",
                  "reportId": "22222222-2222-2222-2222-222222222222",
                  "reportType": "LITTER",
                  "priority": "HIGH",
                  "description": "Overflowing bin at the market square",
                  "location": {"latitude": 48.2081, "longitude": 16.3738, "address": "Market Square 1"}
                }
                """;
    }

    private static void assertNoSetters(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertThat(method.getName())
                    .as("%s must not declare setters", type.getSimpleName())
                    .doesNotStartWith("set");
        }
    }

    private static boolean allFieldsAreFinal(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isFinal(field.getModifiers())) {
                return false;
            }
        }
        return true;
    }
}
