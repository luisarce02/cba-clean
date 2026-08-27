package com.cbclean.incident.application.incident.status;

import com.cbclean.incident.application.incident.get.IncidentNotFoundException;
import com.cbclean.incident.domain.model.*;
import com.cbclean.incident.domain.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateIncidentStatusUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
    private IncidentRepository repo;
    private UpdateIncidentStatusUseCase useCase;

    private Incident freshIncident() {
        return Incident.open(IncidentId.newId(), ReportId.fromString("11111111-1111-1111-1111-111111111111"),
                IncidentType.LITTER, IncidentLocation.of(10, 20), "test", null, NOW);
    }

    @BeforeEach
    void setUp() {
        repo = mock(IncidentRepository.class);
        useCase = new UpdateIncidentStatusUseCase(repo, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void newToAssignedSucceeds() {
        Incident inc = freshIncident();
        when(repo.findById(inc.id())).thenReturn(Optional.of(inc));

        Incident updated = useCase.execute(new UpdateIncidentStatusCommand(inc.id(), IncidentStatus.ASSIGNED, null, "op-1"));

        assertThat(updated.status()).isEqualTo(IncidentStatus.ASSIGNED);
        assertThat(updated.assignment().assigneeId()).isEqualTo("op-1");
        verify(repo).save(updated);
    }

    @Test
    void assignedToInProgressSucceeds() {
        Incident inc = freshIncident();
        inc.assign(Assignment.to("op-1", NOW), NOW);
        when(repo.findById(inc.id())).thenReturn(Optional.of(inc));

        Incident updated = useCase.execute(new UpdateIncidentStatusCommand(inc.id(), IncidentStatus.IN_PROGRESS, null, null));

        assertThat(updated.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
        verify(repo).save(updated);
    }

    @Test
    void inProgressToResolvedSucceeds() {
        Incident inc = freshIncident();
        inc.assign(Assignment.to("op-1", NOW), NOW);
        inc.startWork(NOW);
        when(repo.findById(inc.id())).thenReturn(Optional.of(inc));

        Incident updated = useCase.execute(new UpdateIncidentStatusCommand(inc.id(), IncidentStatus.RESOLVED, "done", null));

        assertThat(updated.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(updated.closingNote()).isEqualTo("done");
        verify(repo).save(updated);
    }

    @Test
    void newToResolvedIsIllegal() {
        Incident inc = freshIncident();
        when(repo.findById(inc.id())).thenReturn(Optional.of(inc));

        assertThatThrownBy(() -> useCase.execute(new UpdateIncidentStatusCommand(inc.id(), IncidentStatus.RESOLVED, "note", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal status transition");
        verify(repo, never()).save(any());
    }

    @Test
    void terminalNoFurtherTransitions() {
        Incident inc = freshIncident();
        inc.assign(Assignment.to("op-1", NOW), NOW);
        inc.startWork(NOW);
        inc.resolve("done", NOW);
        when(repo.findById(inc.id())).thenReturn(Optional.of(inc));

        assertThatThrownBy(() -> useCase.execute(new UpdateIncidentStatusCommand(inc.id(), IncidentStatus.ASSIGNED, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void notFoundThrows404() {
        IncidentId missing = IncidentId.newId();
        when(repo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new UpdateIncidentStatusCommand(missing, IncidentStatus.ASSIGNED, null, null)))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void newToInProgressAutoAssigns() {
        Incident inc = freshIncident();
        when(repo.findById(inc.id())).thenReturn(Optional.of(inc));

        Incident updated = useCase.execute(new UpdateIncidentStatusCommand(inc.id(), IncidentStatus.IN_PROGRESS, null, null));

        assertThat(updated.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
        assertThat(updated.isAssigned()).isTrue();
    }
}
