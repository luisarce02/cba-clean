package com.cbclean.incident.infrastructure.persistence.incident;

import com.cbclean.incident.domain.model.Assignment;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentId;
import com.cbclean.incident.domain.model.IncidentLocation;
import com.cbclean.incident.domain.model.IncidentPriority;
import com.cbclean.incident.domain.model.IncidentStatus;
import com.cbclean.incident.domain.model.IncidentType;
import com.cbclean.incident.domain.model.ReportId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MongoIncidentRepositoryIntegrationTest {

    private static final Instant OPENED_AT = Instant.parse("2026-08-25T08:30:00Z");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private MongoIncidentRepository repository;

    @Autowired
    private IncidentMongoRepository mongoRepository;

    @BeforeEach
    void cleanCollection() {
        mongoRepository.deleteAll();
    }

    private Incident openedIncident() {
        return Incident.open(
                IncidentId.newId(),
                ReportId.fromString("44444444-4444-4444-4444-444444444444"),
                IncidentType.ILLEGAL_DUMPING,
                new IncidentLocation(48.2082, 16.3738, "Danube riverside, Vienna", "Zone 22"),
                "Household waste dumped next to the river",
                IncidentPriority.HIGH,
                OPENED_AT);
    }

    private Incident reloaded(Incident incident) {
        IncidentDocument document = mongoRepository.findById(incident.id().value().toString()).orElseThrow();
        return IncidentPersistenceMapper.toDomain(document);
    }

    @Test
    void incidentCanBePersisted() {
        Incident incident = openedIncident();

        repository.save(incident);

        assertThat(mongoRepository.findById(incident.id().value().toString())).isPresent();
    }

    @Test
    void persistedDataReconstructsDomainAggregate() {
        Incident incident = openedIncident();

        repository.save(incident);
        Incident restored = reloaded(incident);

        assertThat(restored).isEqualTo(incident);
        assertThat(restored.id()).isEqualTo(incident.id());
        assertThat(restored.reportId()).isEqualTo(incident.reportId());
        assertThat(restored.type()).isEqualTo(IncidentType.ILLEGAL_DUMPING);
        assertThat(restored.location()).isEqualTo(incident.location());
        assertThat(restored.description()).isEqualTo("Household waste dumped next to the river");
        assertThat(restored.assignment()).isNull();
        assertThat(restored.closingNote()).isNull();
    }

    @Test
    void uuidIdentitySurvivesRoundTrip() {
        UUID rawId = UUID.randomUUID();
        Incident incident = Incident.open(
                new IncidentId(rawId),
                ReportId.fromString("66666666-6666-6666-6666-666666666666"),
                IncidentType.LITTER,
                IncidentLocation.of(-33.865143, 151.209900),
                null,
                null,
                OPENED_AT);

        repository.save(incident);
        Incident restored = reloaded(incident);

        assertThat(restored.id().value()).isEqualTo(rawId);
        assertThat(restored.id()).isEqualTo(new IncidentId(rawId));
    }

    @Test
    void reportIdSurvivesRoundTrip() {
        ReportId reportId = ReportId.fromString("55555555-5555-5555-5555-555555555555");
        Incident incident = Incident.open(
                IncidentId.newId(),
                reportId,
                IncidentType.OVERFLOWING_BIN,
                IncidentLocation.of(48.2082, 16.3738),
                null,
                null,
                OPENED_AT);

        repository.save(incident);
        Incident restored = reloaded(incident);

        assertThat(restored.reportId()).isEqualTo(reportId);
        assertThat(restored.reportId().value())
                .isEqualTo(UUID.fromString("55555555-5555-5555-5555-555555555555"));
    }

    @Test
    void typePriorityAndStatusSurviveRoundTrip() {
        Instant changedAt = OPENED_AT.plusSeconds(600);
        Incident incident = openedIncident();
        incident.changePriority(IncidentPriority.CRITICAL, changedAt);

        repository.save(incident);
        Incident restored = reloaded(incident);

        assertThat(restored.type()).isEqualTo(IncidentType.ILLEGAL_DUMPING);
        assertThat(restored.priority()).isEqualTo(IncidentPriority.CRITICAL);
        assertThat(restored.status()).isEqualTo(IncidentStatus.NEW);
        assertThat(restored.priority().isEscalated()).isTrue();
    }

    @Test
    void locationAndAssignmentSurviveRoundTrip() {
        Instant assignedAt = OPENED_AT.plusSeconds(300);
        Assignment assignment = Assignment.toTeam("worker-42", "Crew Nord", assignedAt);
        Incident incident = openedIncident();
        incident.assign(assignment, assignedAt);

        repository.save(incident);
        Incident restored = reloaded(incident);

        assertThat(restored.location()).isEqualTo(new IncidentLocation(48.2082, 16.3738, "Danube riverside, Vienna", "Zone 22"));
        assertThat(restored.location().latitude()).isEqualTo(48.2082);
        assertThat(restored.location().longitude()).isEqualTo(16.3738);
        assertThat(restored.assignment()).isEqualTo(assignment);
        assertThat(restored.isAssigned()).isTrue();
    }

    @Test
    void closingInformationSurvivesRoundTrip() {
        Instant resolvedAt = OPENED_AT.plusSeconds(7200);
        Incident incident = openedIncident();
        incident.assign(Assignment.to("worker-7", OPENED_AT.plusSeconds(60)), OPENED_AT.plusSeconds(60));
        incident.startWork(OPENED_AT.plusSeconds(120));
        incident.resolve("Waste collected and site cleaned", resolvedAt);

        repository.save(incident);
        Incident restored = reloaded(incident);

        assertThat(restored.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(restored.isOpen()).isFalse();
        assertThat(restored.closingNote()).isEqualTo("Waste collected and site cleaned");
    }

    @Test
    void timestampsSurviveRoundTrip() {
        Instant lastModifiedAt = OPENED_AT.plusSeconds(3600);
        Incident incident = openedIncident();
        incident.changePriority(IncidentPriority.LOW, lastModifiedAt);

        repository.save(incident);
        Incident restored = reloaded(incident);

        assertThat(restored.createdAt()).isEqualTo(OPENED_AT);
        assertThat(restored.lastModifiedAt()).isEqualTo(lastModifiedAt);
        assertThat(restored.lastModifiedAt().isAfter(restored.createdAt())).isTrue();
    }
}
