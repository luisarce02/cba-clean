package com.cbclean.report.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    @Test
    void submittedReportStartsNewWithNormalPriorityByDefault() {
        Report report = aSubmittedReport(null);

        assertThat(report.status()).isEqualTo(ReportStatus.NEW);
        assertThat(report.priority()).isEqualTo(ReportPriority.NORMAL);
        assertThat(report.isOpen()).isTrue();
        assertThat(report.lastModifiedAt()).isEqualTo(NOW);
    }

    @Test
    void fullLifecycleTransitionsArePossible() {
        Report report = aSubmittedReport(null);

        report.acknowledge(NOW.plusSeconds(60));
        report.startProcessing(NOW.plusSeconds(120));
        report.resolve("Crew collected the waste", NOW.plusSeconds(3600));

        assertThat(report.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.isOpen()).isFalse();
        assertThat(report.closingNote()).isEqualTo("Crew collected the waste");
        assertThat(report.lastModifiedAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void illegalTransitionIsRejected() {
        Report report = aSubmittedReport(null);

        assertThatThrownBy(() -> report.resolve("skipping steps", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal status transition from NEW to RESOLVED");
    }

    @Test
    void closedReportCannotBeModified() {
        Report report = aSubmittedReport(null);
        report.cancel("Duplicate of another report", NOW);

        assertThatThrownBy(() -> report.acknowledge(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed report");
        assertThatThrownBy(() -> report.changePriority(ReportPriority.HIGH, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolveWithoutNoteIsRejected() {
        Report report = inProgressReport();

        assertThatThrownBy(() -> report.resolve("  ", NOW))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("resolution note");
    }

    @Test
    void cancelWithoutReasonIsRejected() {
        Report report = inProgressReport();

        assertThatThrownBy(() -> report.cancel(null, NOW))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void priorityCanBeChangedWhileOpen() {
        Report report = aSubmittedReport(null);

        report.changePriority(ReportPriority.CRITICAL, NOW.plusSeconds(30));

        assertThat(report.priority()).isEqualTo(ReportPriority.CRITICAL);
    }

    @Test
    void missingTypeOrLocationIsRejected() {
        GeoLocation location = GeoLocation.of(48.2082, 16.3738);

        assertThatThrownBy(() ->
                Report.submit(ReportId.newId(), null, location, null, null, null, null, NOW))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("type is required");

        assertThatThrownBy(() ->
                Report.submit(ReportId.newId(), ReportType.LITTER, null, null, null, null, null, NOW))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("location is required");
    }

    @Test
    void descriptionIsTrimmedAndBounded() {
        Report report = aSubmittedReport("  Waste dumped next to the river  ");
        assertThat(report.description()).isEqualTo("Waste dumped next to the river");

        String tooLong = "x".repeat(2001);
        assertThatThrownBy(() -> aSubmittedReport(tooLong))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void photoIdsAreStoredUnmodifiableAndValidated() {
        Report report = aSubmittedPhotoReport(List.of("photo-1", "photo-2"));

        assertThat(report.photoIds()).containsExactly("photo-1", "photo-2");
        assertThatThrownBy(() -> report.photoIds().add("photo-3"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> aSubmittedPhotoReport(List.of("", " ")))
                .isInstanceOf(InvalidReportException.class);
    }

    @Test
    void reportsWithSameIdAreEqualRegardlessOfState() {
        ReportId sharedId = ReportId.newId();
        Report first = submitWithId(sharedId);
        Report second = submitWithId(sharedId);

        second.acknowledge(NOW);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    private Report aSubmittedReport(String description) {
        return Report.submit(
                ReportId.newId(),
                ReportType.ILLEGAL_DUMPING,
                GeoLocation.of(48.2082, 16.3738),
                Reporter.anonymous(),
                description,
                null,
                null,
                NOW);
    }

    private Report aSubmittedPhotoReport(List<String> photoIds) {
        return Report.submit(
                ReportId.newId(),
                ReportType.OVERFLOWING_BIN,
                GeoLocation.of(48.2082, 16.3738),
                Reporter.anonymous(),
                null,
                photoIds,
                null,
                NOW);
    }

    private Report submitWithId(ReportId id) {
        return Report.submit(
                id,
                ReportType.LITTER,
                GeoLocation.of(48.2082, 16.3738),
                Reporter.anonymous(),
                null,
                null,
                null,
                NOW);
    }

    private Report inProgressReport() {
        Report report = aSubmittedReport(null);
        report.acknowledge(NOW);
        report.startProcessing(NOW);
        return report;
    }
}
