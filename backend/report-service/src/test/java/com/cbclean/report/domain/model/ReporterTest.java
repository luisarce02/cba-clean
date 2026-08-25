package com.cbclean.report.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReporterTest {

    @Test
    void anonymousReporterHasNoContactDetails() {
        Reporter reporter = Reporter.anonymous();

        assertThat(reporter.isAnonymous()).isTrue();
        assertThat(reporter.name()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jane@example.com", "jane.doe+clean@sub.example.co"})
    void acceptsValidEmails(String email) {
        assertThat(new Reporter("Jane", email, "+43 1 234567").email()).isEqualTo(email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "jane@", "@example.com", "jane@example"})
    void rejectsInvalidEmails(String email) {
        assertThatThrownBy(() -> new Reporter(null, email, null))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsInvalidPhoneNumbers() {
        assertThatThrownBy(() -> new Reporter(null, null, "call me maybe"))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("phone");
    }

    @Test
    void nameWithoutAnyContactDetailIsRejected() {
        assertThatThrownBy(() -> new Reporter("Jane Doe", null, null))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("anonymous");
    }

    @Test
    void blankFieldsAreNormalizedToNull() {
        Reporter reporter = new Reporter("  ", "jane@example.com", "");

        assertThat(reporter.name()).isNull();
        assertThat(reporter.phone()).isNull();
        assertThat(reporter.isAnonymous()).isFalse();
    }
}
