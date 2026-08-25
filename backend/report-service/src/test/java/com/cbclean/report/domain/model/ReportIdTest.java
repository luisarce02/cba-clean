package com.cbclean.report.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportIdTest {

    @Test
    void parsesValidUuidString() {
        UUID value = UUID.randomUUID();

        assertThat(ReportId.fromString(value.toString()).value()).isEqualTo(value);
        assertThat(new ReportId(value)).isEqualTo(ReportId.fromString("  " + value + "  "));
    }

    @Test
    void rejectsBlankAndMalformedStrings() {
        assertThatThrownBy(() -> ReportId.fromString(null))
                .isInstanceOf(InvalidReportException.class);
        assertThatThrownBy(() -> ReportId.fromString("   "))
                .isInstanceOf(InvalidReportException.class);
        assertThatThrownBy(() -> ReportId.fromString("not-a-uuid"))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("valid UUID");
    }
}
