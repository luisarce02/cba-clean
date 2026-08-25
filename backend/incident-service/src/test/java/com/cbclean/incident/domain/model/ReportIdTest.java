package com.cbclean.incident.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportIdTest {

    @Test
    void fromStringParsesValidUuid() {
        UUID uuid = UUID.randomUUID();

        ReportId reportId = ReportId.fromString(uuid.toString());

        assertThat(reportId.value()).isEqualTo(uuid);
        assertThat(reportId).isEqualTo(new ReportId(uuid));
    }

    @Test
    void toStringRendersTheUuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(ReportId.fromString(uuid.toString()).toString()).isEqualTo(uuid.toString());
    }

    @Test
    void nullValueIsRejected() {
        assertThatThrownBy(() -> new ReportId(null))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void blankOrMalformedValuesAreRejected() {
        assertThatThrownBy(() -> ReportId.fromString(" "))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> ReportId.fromString("abc"))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("valid UUID");
    }
}
