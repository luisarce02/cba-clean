package com.cbclean.incident.infrastructure.metrics;

import com.cbclean.incident.infrastructure.messaging.MessagingTopology;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import com.cbclean.incident.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exposed Actuator surface of the Incident Service: health stays
 * public, the custom business metrics appear in {@code /actuator/metrics} and
 * {@code /actuator/prometheus} for authenticated OPERATOR access (metrics are
 * intentionally not public), and sensitive endpoints remain disabled.
 * A real event flows through RabbitMQ so counters have observable values.
 */
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
class MetricsEndpointIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

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
    private RabbitTemplate rabbitTemplate;

    private ResponseEntity<String> getAsOperator(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestJwts.token("operator-1", List.of("OPERATOR")));
        return rest.exchange("http://localhost:" + environment.getProperty("local.server.port") + path,
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void publishOneValidEvent() {
        rabbitTemplate.convertAndSend(
                MessagingTopology.EVENTS_EXCHANGE,
                MessagingTopology.REPORT_CREATED_ROUTING_KEY,
                new ReportCreatedEvent(
                        UUID.randomUUID(),
                        Instant.parse("2026-08-25T12:00:00Z"),
                        UUID.randomUUID(),
                        "LITTER",
                        "LOW",
                        "Metrics endpoint check",
                        new ReportCreatedEvent.Location(48.2, 16.4, null)));
    }

    @Test
    void healthEndpointRemainsPubliclyAvailable() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void metricsAreNotPubliclyAccessible() {
        ResponseEntity<String> metrics = rest.getForEntity("/actuator/metrics", String.class);
        ResponseEntity<String> prometheus = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void metricsEndpointExposesTheCustomBusinessMetricsToOperators() throws Exception {
        publishOneValidEvent();
        awaitIncidentProcessing();

        ResponseEntity<String> index = getAsOperator("/actuator/metrics");
        assertThat(index.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (String metric : new String[]{
                "cbaclean.incidents.created",
                "cbaclean.incident.events.processed",
                "cbaclean.incident.event.processing.duration"}) {
            assertThat(index.getBody()).as("metric %s listed", metric).contains(metric);
            ResponseEntity<String> single = getAsOperator("/actuator/metrics/" + metric);
            assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void prometheusEndpointExposesTheCustomBusinessMetricsToOperators() throws Exception {
        publishOneValidEvent();
        awaitIncidentProcessing();

        ResponseEntity<String> response = getAsOperator("/actuator/prometheus");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Prometheus reserves the _created suffix: cbaclean.incidents.created
        // is exposed as cbaclean_incidents_total.
        assertThat(response.getBody()).contains("cbaclean_incidents_total");
        assertThat(response.getBody()).contains("cbaclean_incident_events_processed");
        assertThat(response.getBody()).contains("cbaclean_incident_event_processing_duration");
    }

    @Test
    void sensitiveEndpointsAreNotExposed() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestJwts.token("operator-1", List.of("OPERATOR")));

        for (String endpoint : new String[]{
                "env", "beans", "configprops", "mappings", "heapdump", "threaddump", "loggers"}) {
            ResponseEntity<String> response = rest.exchange(
                    "/actuator/" + endpoint,
                    org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            assertThat(response.getStatusCode())
                    .as("%s must not be exposed", endpoint)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }

    private void awaitIncidentProcessing() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<String> created =
                    getAsOperator("/actuator/metrics/cbaclean.incidents.created");
            if (created.getStatusCode().is2xxSuccessful() && created.getBody() != null
                    && !created.getBody().contains("\"count\":0")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("incidents.created counter was not incremented in time");
    }
}
