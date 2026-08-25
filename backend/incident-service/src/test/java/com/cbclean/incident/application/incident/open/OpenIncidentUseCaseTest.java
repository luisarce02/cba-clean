package com.cbclean.incident.application.incident.open;

import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentLocation;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentStatus;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.domain.model.InvalidIncidentException;
import com.cbclean.incident.domain.model.ReportId;
import com.cbclean.incident.domain.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OpenIncidentUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    private final IncidentRepository repository = mock(IncidentRepository.class);
    private final OpenIncidentUseCase useCase = new OpenIncidentUseCase(
            repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void validCommandCreatesAndSavesAnIncident() {
        ReportId reportId = ReportId.fromString("11111111-1111-1111-1111-111111111111");
        OpenIncidentCommand command = new OpenIncidentCommand(
                reportId,
                IncidentType.BULKY_WASTE,
                new IncidentLocation(48.2082, 16.3738, "Rathausplatz 1", "Zone A"),
                " Large pothole on the main road ",
                IncidentPriority.HIGH);

        Incident result = useCase.execute(command);

        assertThat(result.id()).isNotNull();
        assertThat(result.reportId()).isEqualTo(reportId);
        assertThat(result.type()).isEqualTo(IncidentType.BULKY_WASTE);
        assertThat(result.status()).isEqualTo(IncidentStatus.NEW);
        assertThat(result.priority()).isEqualTo(IncidentPriority.HIGH);
        assertThat(result.location().address()).isEqualTo("Rathausplatz 1");
        assertThat(result.description()).isEqualTo("Large pothole on the main road");
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.lastModifiedAt()).isEqualTo(NOW);

        verify(repository).save(result);
    }

    @Test
    void repositoryIsCalledExactlyOncePerExecution() {
        OpenIncidentCommand command = new OpenIncidentCommand(
                ReportId.fromString("22222222-2222-2222-2222-222222222222"),
                IncidentType.LITTER,
                IncidentLocation.of(48.2082, 16.3738),
                null,
                null);

        useCase.execute(command);

        verify(repository, times(1)).save(any(Incident.class));
    }

    @Test
    void domainValidationFailuresPreventPersistence() {
        OpenIncidentCommand missingReport = new OpenIncidentCommand(
                null,
                IncidentType.LITTER,
                IncidentLocation.of(48.2082, 16.3738),
                "Broken glass",
                null);

        assertThatThrownBy(() -> useCase.execute(missingReport))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("report id");

        OpenIncidentCommand badDescription = new OpenIncidentCommand(
                ReportId.fromString("33333333-3333-3333-3333-333333333333"),
                IncidentType.LITTER,
                IncidentLocation.of(48.2082, 16.3738),
                "x".repeat(2001),
                null);

        assertThatThrownBy(() -> useCase.execute(badDescription))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("Description");

        verify(repository, never()).save(any(Incident.class));
    }
}
