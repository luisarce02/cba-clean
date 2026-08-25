package com.cbclean.report.infrastructure.persistence.report;

import com.cbclean.report.application.port.ReportEventPublisher;
import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.InvalidReportException;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.domain.model.ReportPriority;
import com.cbclean.report.domain.model.ReportStatus;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.domain.model.Reporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PostgresReportRepositoryIntegrationTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-08-25T08:30:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private PostgresReportRepository repository;

    @Autowired
    private ReportJpaRepository jpaRepository;

    @Autowired
    private SubmitReportUseCase submitReportUseCase;

    /**
     * Persistence-focused tests must not depend on a RabbitMQ broker; event
     * publication itself is covered by dedicated messaging tests.
     */
    @MockitoBean
    private ReportEventPublisher reportEventPublisher;

    @BeforeEach
    void cleanDatabase() {
        jpaRepository.deleteAll();
    }

    @Test
    void validReportCanBePersistedAndReconstructed() {
        Reporter reporter = new Reporter("Jane Doe", "jane@example.com", "+43 1 2345678");
        Report report = Report.submit(
                ReportId.newId(),
                ReportType.ILLEGAL_DUMPING,
                new GeoLocation(48.2082, 16.3738, "  Danube riverside, Vienna "),
                reporter,
                "  Household waste dumped next to the river  ",
                List.of("photo-1", "photo-2"),
                ReportPriority.HIGH,
                SUBMITTED_AT);

        repository.save(report);

        Report reloaded = ReportPersistenceMapper.toDomain(jpaRepository.findById(report.id().value()).orElseThrow());
        assertThat(reloaded.id()).isEqualTo(report.id());
        assertThat(reloaded.type()).isEqualTo(ReportType.ILLEGAL_DUMPING);
        assertThat(reloaded.location()).isEqualTo(new GeoLocation(48.2082, 16.3738, "Danube riverside, Vienna"));
        assertThat(reloaded.location().latitude()).isEqualTo(48.2082);
        assertThat(reloaded.location().longitude()).isEqualTo(16.3738);
        assertThat(reloaded.reporter()).isEqualTo(reporter);
        assertThat(reloaded.description()).isEqualTo("Household waste dumped next to the river");
        assertThat(reloaded.photoIds()).containsExactly("photo-1", "photo-2");
        assertThat(reloaded.createdAt()).isEqualTo(SUBMITTED_AT);
        assertThat(reloaded.lastModifiedAt()).isEqualTo(SUBMITTED_AT);
    }

    @Test
    void valueObjectsSurvivePersistenceRoundTrip() {
        Report report = Report.submit(
                ReportId.newId(),
                ReportType.OVERFLOWING_BIN,
                GeoLocation.of(-33.865143, 151.209900),
                Reporter.anonymous(),
                null,
                List.of(),
                null,
                SUBMITTED_AT);

        repository.save(report);

        Report reloaded = ReportPersistenceMapper.toDomain(jpaRepository.findById(report.id().value()).orElseThrow());
        assertThat(reloaded.location()).isEqualTo(GeoLocation.of(-33.865143, 151.209900));
        assertThat(reloaded.reporter().isAnonymous()).isTrue();
        assertThat(reloaded.description()).isNull();
        assertThat(reloaded.photoIds()).isEmpty();
        assertThat(reloaded.closingNote()).isNull();
    }

    @Test
    void statusAndPriorityArePersistedCorrectly() {
        Report report = Report.submit(
                ReportId.newId(),
                ReportType.BULKY_WASTE,
                GeoLocation.of(48.2082, 16.3738),
                null,
                "Old sofa on the sidewalk",
                List.of(),
                null,
                SUBMITTED_AT);
        Instant acknowledgedAt = SUBMITTED_AT.plusSeconds(3600);
        report.acknowledge(acknowledgedAt);
        report.changePriority(ReportPriority.CRITICAL, acknowledgedAt.plusSeconds(60));
        repository.save(report);

        Report reloaded = ReportPersistenceMapper.toDomain(jpaRepository.findById(report.id().value()).orElseThrow());
        assertThat(reloaded.status()).isEqualTo(ReportStatus.ACKNOWLEDGED);
        assertThat(reloaded.priority()).isEqualTo(ReportPriority.CRITICAL);
        assertThat(reloaded.lastModifiedAt()).isEqualTo(acknowledgedAt.plusSeconds(60));

        Instant resolvedAt = acknowledgedAt.plusSeconds(7200);
        report.startProcessing(resolvedAt.minusSeconds(600));
        report.resolve("Collected by waste management", resolvedAt);
        repository.save(report);

        Report closed = ReportPersistenceMapper.toDomain(jpaRepository.findById(report.id().value()).orElseThrow());
        assertThat(closed.status()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.closingNote()).isEqualTo("Collected by waste management");
    }

    @Test
    void generatedIdentifierIsHandledCorrectly() {
        Report submitted = submitReportUseCase.execute(new SubmitReportCommand(
                ReportType.LITTER,
                "Cigarette butts on the playground",
                GeoLocation.of(48.2082, 16.3738),
                null,
                List.of()));

        UUID generatedId = submitted.id().value();
        assertThat(generatedId).isNotNull();
        ReportEntity persisted = jpaRepository.findById(generatedId).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(generatedId);

        Report reloaded = ReportPersistenceMapper.toDomain(persisted);
        assertThat(reloaded.id()).isEqualTo(submitted.id());
        assertThat(new ReportId(generatedId)).isEqualTo(submitted.id());
    }

    @Test
    void findByIdReturnsPersistedReport() {
        Report report = Report.submit(
                ReportId.newId(),
                ReportType.ILLEGAL_DUMPING,
                new GeoLocation(48.2082, 16.3738, "Danube riverside, Vienna"),
                new Reporter("Jane Doe", "jane@example.com", "+43 1 2345678"),
                "Household waste dumped next to the river",
                List.of("photo-1", "photo-2"),
                ReportPriority.HIGH,
                SUBMITTED_AT);
        repository.save(report);

        Optional<Report> loaded = repository.findById(report.id());

        assertThat(loaded).isPresent();
        Report reloaded = loaded.orElseThrow();
        assertThat(reloaded.id()).isEqualTo(report.id());
        assertThat(reloaded.type()).isEqualTo(ReportType.ILLEGAL_DUMPING);
        assertThat(reloaded.status()).isEqualTo(ReportStatus.NEW);
        assertThat(reloaded.priority()).isEqualTo(ReportPriority.HIGH);
        assertThat(reloaded.location()).isEqualTo(new GeoLocation(48.2082, 16.3738, "Danube riverside, Vienna"));
        assertThat(reloaded.reporter()).isEqualTo(new Reporter("Jane Doe", "jane@example.com", "+43 1 2345678"));
        assertThat(reloaded.description()).isEqualTo("Household waste dumped next to the river");
        assertThat(reloaded.photoIds()).containsExactly("photo-1", "photo-2");
        assertThat(reloaded.createdAt()).isEqualTo(SUBMITTED_AT);
        assertThat(reloaded.lastModifiedAt()).isEqualTo(SUBMITTED_AT);
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(ReportId.newId())).isEmpty();
    }

    @Test
    void invalidDomainStateCannotBePersistedThroughDomainApi() {
        assertThatThrownBy(() -> submitReportUseCase.execute(new SubmitReportCommand(
                ReportType.LITTER,
                "x".repeat(2001),
                GeoLocation.of(48.2082, 16.3738),
                null,
                List.of())))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("Description");

        Report report = Report.submit(
                ReportId.newId(),
                ReportType.LITTER,
                GeoLocation.of(48.2082, 16.3738),
                null,
                "Broken glass",
                List.of(),
                null,
                SUBMITTED_AT);
        assertThatThrownBy(() -> report.resolve(null, SUBMITTED_AT.plusSeconds(60)))
                .isInstanceOf(InvalidReportException.class)
                .hasMessageContaining("resolution note");
        repository.save(report);

        Report persisted = ReportPersistenceMapper.toDomain(jpaRepository.findById(report.id().value()).orElseThrow());
        assertThat(persisted.status()).isEqualTo(ReportStatus.NEW);
        assertThat(persisted.closingNote()).isNull();
        assertThat(jpaRepository.count()).isEqualTo(1);
    }
}
