package com.cbclean.report.presentation.metrics;

import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.ReportType;
import com.cbclean.report.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exposed Actuator surface: health stays public, the custom
 * business metrics appear in {@code /actuator/metrics} and
 * {@code /actuator/prometheus} for authenticated OPERATOR access (metrics are
 * intentionally not public), and sensitive endpoints remain disabled.
 */
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "cbaclean.outbox.poll-interval=PT1H"
        })
@Testcontainers
class MetricsEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Locally signed JWTs instead of an external identity provider. */
    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        @Primary
        JwtDecoder jwtDecoder() {
            return TestJwts.decoder();
        }
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Autowired
    private SubmitReportUseCase submitReport;

    private String metricsUrl(String path) {
        return "http://localhost:" + environment.getProperty("local.server.port") + path;
    }

    private ResponseEntity<String> getAsOperator(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestJwts.token("operator-1", List.of("OPERATOR")));
        return rest.exchange(metricsUrl(path), org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private void submitOneReport() {
        submitReport.execute(new SubmitReportCommand(
                ReportType.LITTER, "Metrics endpoint check",
                GeoLocation.of(48.2, 16.3), null, null));
    }

    @Test
    void healthEndpointRemainsPubliclyAvailable() {
        ResponseEntity<String> response = rest.getForEntity(metricsUrl("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void metricsEndpointExposesTheCustomBusinessMetricsToOperators() {
        submitOneReport();

        ResponseEntity<String> index = getAsOperator("/actuator/metrics");
        assertThat(index.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (String metric : new String[]{
                "cbaclean.reports.created",
                "cbaclean.outbox.events.pending",
                "cbaclean.report.creation.duration"}) {
            assertThat(index.getBody()).as("metric %s listed", metric).contains(metric);
            ResponseEntity<String> single = getAsOperator("/actuator/metrics/" + metric);
            assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void prometheusEndpointExposesTheCustomBusinessMetricsToOperators() {
        submitOneReport();

        ResponseEntity<String> response = getAsOperator("/actuator/prometheus");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Note: Prometheus reserves the _created suffix, so
        // cbaclean.reports.created is exposed as cbaclean_reports_total.
        assertThat(response.getBody()).contains("cbaclean_reports_total");
        assertThat(response.getBody()).contains("cbaclean_outbox_events_pending");
        assertThat(response.getBody()).contains("cbaclean_report_creation_duration");
    }

    @Test
    void sensitiveEndpointsAreNotExposed() {
        String operator = TestJwts.token("operator-1", List.of("OPERATOR"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(operator);

        for (String endpoint : new String[]{
                "env", "beans", "configprops", "mappings", "heapdump", "threaddump", "loggers"}) {
            ResponseEntity<String> response = rest.exchange(
                    metricsUrl("/actuator/" + endpoint),
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            assertThat(response.getStatusCode())
                    .as("%s must not be exposed", endpoint)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }
}
