package com.cbclean.incident.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");
    private static final ReportId REPORT_ID = ReportId.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void openedIncidentStartsNewUnassignedWithNormalPriority() {
        Incident incident = anOpenedIncident(null);

        assertThat(incident.status()).isEqualTo(IncidentStatus.NEW);
        assertThat(incident.priority()).isEqualTo(IncidentPriority.NORMAL);
        assertThat(incident.isAssigned()).isFalse();
        assertThat(incident.assignment()).isNull();
        assertThat(incident.isOpen()).isTrue();
        assertThat(incident.closingNote()).isNull();
        assertThat(incident.createdAt()).isEqualTo(NOW);
        assertThat(incident.lastModifiedAt()).isEqualTo(NOW);
    }

    @Test
    void openedIncidentKeepsOriginatingReportReferenceAndType() {
        Incident incident = anOpenedIncident(null);

        assertThat(incident.reportId()).isEqualTo(REPORT_ID);
        assertThat(incident.type()).isEqualTo(IncidentType.ILLEGAL_DUMPING);
    }

    @Test
    void missingRequiredValuesAreRejected() {
        IncidentLocation location = IncidentLocation.of(10.0, 20.0);

        assertThatThrownBy(() -> Incident.open(null, REPORT_ID, IncidentType.LITTER,
                location, null, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("Incident id is required");

        assertThatThrownBy(() -> Incident.open(IncidentId.newId(), null, IncidentType.LITTER,
                location, null, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("report id is required");

        assertThatThrownBy(() -> Incident.open(IncidentId.newId(), REPORT_ID, null,
                location, null, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("type is required");

        assertThatThrownBy(() -> Incident.open(IncidentId.newId(), REPORT_ID,
                IncidentType.LITTER, null, null, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("location is required");

        assertThatThrownBy(() -> Incident.open(IncidentId.newId(), REPORT_ID,
                IncidentType.LITTER, location, null, null, null))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("creation time is required");
    }

    @Test
    void blankDescriptionIsNormalizedToNullAndLongDescriptionRejected() {
        Incident blank = anOpenedIncident("   ");
        assertThat(blank.description()).isNull();

        String tooLong = "d".repeat(2001);
        assertThatThrownBy(() -> anOpenedIncident(tooLong))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void fullLifecycleFromNewToResolved() {
        Instant start = NOW.plusSeconds(600);
        Instant end = NOW.plusSeconds(3600);

        Incident incident = anOpenedIncident(null);
        incident.assign(Assignment.toTeam("worker-1", "North Crew", NOW.plusSeconds(60)),
                NOW.plusSeconds(60));
        incident.startWork(start);
        incident.resolve("Waste collected by crew", end);

        assertThat(incident.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.isAssigned()).isTrue();
        assertThat(incident.assignment().assigneeId()).isEqualTo("worker-1");
        assertThat(incident.closingNote()).isEqualTo("Waste collected by crew");
        assertThat(incident.lastModifiedAt()).isEqualTo(end);
        assertThat(incident.isOpen()).isFalse();
    }

    @Test
    void newIncidentCannotSkipAssignmentAndStartWork() {
        Incident incident = anOpenedIncident(null);

        assertThatThrownBy(() -> incident.startWork(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unassigned");
    }

    @Test
    void newIncidentCannotBeResolvedDirectly() {
        Incident incident = anOpenedIncident(null);

        assertThatThrownBy(() -> incident.resolve("skipping steps", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal status transition from NEW to RESOLVED");
    }

    @Test
    void workCannotStartWithoutAssignmentEvenAfterOtherTransitions() {
        Incident incident = anOpenedIncident(null);
        incident.cancel("Duplicate", NOW);
        Incident reopened = anOpenedIncident(null);
        reopened.assign(Assignment.to("worker-1", NOW), NOW);
        reopened.startWork(NOW.plusSeconds(10));

        assertThat(reopened.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThatThrownBy(() -> incident.startWork(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assignmentMovesNewIncidentToAssignedAndRecordsTimestamp() {
        Instant assignedAt = NOW.plusSeconds(120);

        Incident incident = anOpenedIncident(null);
        incident.assign(Assignment.to("worker-7", assignedAt), assignedAt);

        assertThat(incident.status()).isEqualTo(IncidentStatus.ASSIGNED);
        assertThat(incident.assignment().assigneeId()).isEqualTo("worker-7");
        assertThat(incident.assignment().assignedAt()).isEqualTo(assignedAt);
        assertThat(incident.lastModifiedAt()).isEqualTo(assignedAt);
    }

    @Test
    void reassignmentReplacesWorkerWhileStillAssigned() {
        Instant reassignedAt = NOW.plusSeconds(300);

        Incident incident = aNewAssignedIncident();
        incident.assign(Assignment.toTeam("worker-2", "South Crew", reassignedAt), reassignedAt);

        assertThat(incident.status()).isEqualTo(IncidentStatus.ASSIGNED);
        assertThat(incident.assignment().assigneeId()).isEqualTo("worker-2");
        assertThat(incident.lastModifiedAt()).isEqualTo(reassignedAt);
    }

    @Test
    void assigningNullIsRejected() {
        Incident incident = anOpenedIncident(null);

        assertThatThrownBy(() -> incident.assign(null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Assignment is required");
    }

    @Test
    void inProgressIncidentCannotBeReassigned() {
        Incident incident = anInProgressIncident();

        assertThatThrownBy(() ->
                incident.assign(Assignment.to("worker-9", NOW), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("work is in progress");
    }

    @Test
    void resolveRequiresResolutionNote() {
        Incident incident = anInProgressIncident();

        assertThatThrownBy(() -> incident.resolve(null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("resolution note");

        assertThatThrownBy(() -> incident.resolve("   ", NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("resolution note");
    }

    @Test
    void cancelIsPossibleFromEveryNonTerminalStateAndRequiresReason() {
        Incident fresh = anOpenedIncident(null);
        fresh.cancel("Report was a false alarm", NOW.plusSeconds(10));
        assertThat(fresh.status()).isEqualTo(IncidentStatus.CANCELLED);

        Incident assigned = aNewAssignedIncident();
        assigned.cancel("Duplicate of incident 42", NOW.plusSeconds(10));
        assertThat(assigned.status()).isEqualTo(IncidentStatus.CANCELLED);

        Incident inProgress = anInProgressIncident();
        inProgress.cancel("Site already cleared by another crew", NOW.plusSeconds(10));
        assertThat(inProgress.status()).isEqualTo(IncidentStatus.CANCELLED);
    }

    @Test
    void cancelWithoutReasonIsRejected() {
        Incident incident = anOpenedIncident(null);

        assertThatThrownBy(() -> incident.cancel(null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("without a reason");

        assertThatThrownBy(() -> incident.cancel("  ", NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("without a reason");
    }

    @Test
    void resolvedIncidentIsImmutable() {
        Incident incident = aResolvedIncident();
        Instant before = incident.lastModifiedAt();

        assertThatThrownBy(() -> incident.assign(Assignment.to("worker-1", NOW), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal incident");
        assertThatThrownBy(() -> incident.resolve("again", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> incident.cancel("again", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> incident.changePriority(IncidentPriority.CRITICAL, NOW))
                .isInstanceOf(IllegalStateException.class);

        assertThat(incident.lastModifiedAt()).isEqualTo(before);
    }

    @Test
    void cancelledIncidentIsImmutable() {
        Incident incident = anOpenedIncident(null);
        incident.cancel("No action needed", NOW);

        assertThatThrownBy(() -> incident.assign(Assignment.to("worker-1", NOW), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> incident.changePriority(IncidentPriority.LOW, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void priorityCanBeChangedWhileOpenButNotRepeatedForSameValue() {
        Incident incident = aNewAssignedIncident();

        incident.changePriority(IncidentPriority.HIGH, NOW.plusSeconds(30));
        assertThat(incident.priority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(incident.lastModifiedAt()).isEqualTo(NOW.plusSeconds(30));

        Instant before = incident.lastModifiedAt();
        incident.changePriority(IncidentPriority.HIGH, NOW.plusSeconds(60));
        assertThat(incident.lastModifiedAt()).isEqualTo(before);
    }

    @Test
    void changingPriorityToNullIsRejected() {
        Incident incident = aNewAssignedIncident();

        assertThatThrownBy(() -> incident.changePriority(null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("priority is required");
    }

    @Test
    void identityEqualityIgnoresMutableState() {
        Incident first = anOpenedIncident(null);
        Incident second = Incident.reconstitute(
                first.id(),
                first.reportId(),
                first.type(),
                first.location(),
                first.description(),
                IncidentStatus.IN_PROGRESS,
                IncidentPriority.CRITICAL,
                Assignment.to("someone", NOW),
                null,
                first.createdAt(),
                NOW.plusSeconds(5));

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);

        first.assign(Assignment.to("worker-1", NOW), NOW);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void reconstitutePreservesAllStateIncludingTerminalState() {
        Instant closedAt = NOW.plusSeconds(7200);

        Incident source = anInProgressIncident();
        source.resolve("Done", closedAt);

        Incident restored = Incident.reconstitute(
                source.id(), source.reportId(), source.type(), source.location(),
                source.description(), source.status(), source.priority(),
                source.assignment(), source.closingNote(),
                source.createdAt(), source.lastModifiedAt());

        assertThat(restored.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(restored.closingNote()).isEqualTo("Done");
        assertThat(restored.lastModifiedAt()).isEqualTo(closedAt);
        assertThat(restored.assignment()).isEqualTo(source.assignment());
    }

    @Test
    void toStringContainsKeyOperationalFacts() {
        Incident incident = anOpenedIncident(null);

        assertThat(incident.toString())
                .contains(incident.id().toString())
                .contains("ILLEGAL_DUMPING")
                .contains("NEW")
                .contains("NORMAL");
    }

    private Incident anOpenedIncident(String description) {
        return Incident.open(
                IncidentId.newId(),
                REPORT_ID,
                IncidentType.ILLEGAL_DUMPING,
                new IncidentLocation(50.4501, 30.5234, "Riverside Road 12", "ZONE-NORTH"),
                description,
                null,
                NOW);
    }

    private Incident aNewAssignedIncident() {
        Incident incident = anOpenedIncident(null);
        incident.assign(Assignment.toTeam("worker-1", "North Crew", NOW.plusSeconds(60)),
                NOW.plusSeconds(60));
        return incident;
    }

    private Incident anInProgressIncident() {
        Incident incident = aNewAssignedIncident();
        incident.startWork(NOW.plusSeconds(600));
        return incident;
    }

    private Incident aResolvedIncident() {
        Incident incident = anInProgressIncident();
        incident.resolve("Cleared", NOW.plusSeconds(3600));
        return incident;
    }
}
