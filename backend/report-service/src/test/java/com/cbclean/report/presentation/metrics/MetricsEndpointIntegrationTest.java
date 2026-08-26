package com.cbclean.report.presentation.metrics;

import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.ReportType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exposed Actuator surface: health stays available, the custom
 * business metrics appear in {@code /actuator/metrics} and
 * {@code /actuator/prometheus}, and sensitive endpoints remain disabled.
 */
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MetricsEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SubmitReportUseCase submitReport;

    private void submitOneReport() {
        submitReport.execute(new SubmitReportCommand(
                ReportType.LITTER, "Metrics endpoint check",
                GeoLocation.of(48.2, 16.3), null, null));
    }

    @Test
    void healthEndpointRemainsAvailable() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void metricsEndpointExposesTheCustomBusinessMetrics() {
        submitOneReport();

        ResponseEntity<String> index = rest.getForEntity("/actuator/metrics", String.class);
        assertThat(index.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (String metric : new String[]{
                "cbaclean.reports.created",
                "cbaclean.report.events.published",
                "cbaclean.report.creation.duration"}) {
            assertThat(index.getBody()).as("metric %s listed", metric).contains(metric);
            ResponseEntity<String> single =
                    rest.getForEntity("/actuator/metrics/" + metric, String.class);
            assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(single.getBody()).contains("COUNT");
        }
    }

    @Test
    void prometheusEndpointExposesTheCustomBusinessMetrics() {
        submitOneReport();

        ResponseEntity<String> response = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Note: Prometheus reserves the _created suffix, so
        // cbaclean.reports.created is exposed as cbaclean_reports_total.
        assertThat(response.getBody()).contains("cbaclean_reports_total");
        assertThat(response.getBody()).contains("cbaclean_report_events_published");
        assertThat(response.getBody()).contains("cbaclean_report_creation_duration");
    }

    @Test
    void sensitiveEndpointsAreNotExposed() {
        for (String endpoint : new String[]{
                "env", "beans", "configprops", "mappings", "heapdump", "threaddump", "loggers"}) {
            ResponseEntity<String> response = rest.getForEntity("/actuator/" + endpoint, String.class);
            assertThat(response.getStatusCode())
                    .as("%s must not be exposed", endpoint)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }
}
