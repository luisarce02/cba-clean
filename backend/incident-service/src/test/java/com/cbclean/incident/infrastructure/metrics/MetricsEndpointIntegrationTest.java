package com.cbclean.incident.infrastructure.metrics;

import com.cbclean.incident.infrastructure.messaging.MessagingTopology;
import com.cbclean.incident.integration.event.ReportCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the exposed Actuator surface of the Incident Service: health stays
 * available, the custom business metrics appear in {@code /actuator/metrics}
 * and {@code /actuator/prometheus}, and sensitive endpoints remain disabled.
 * A real event flows through RabbitMQ so counters have observable values.
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
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
    void healthEndpointRemainsAvailable() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void metricsEndpointExposesTheCustomBusinessMetrics() throws Exception {
        publishOneValidEvent();
        awaitIncidentProcessing();

        ResponseEntity<String> index = rest.getForEntity("/actuator/metrics", String.class);
        assertThat(index.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (String metric : new String[]{
                "cbaclean.incidents.created",
                "cbaclean.incident.events.processed",
                "cbaclean.incident.event.processing.duration"}) {
            assertThat(index.getBody()).as("metric %s listed", metric).contains(metric);
            ResponseEntity<String> single =
                    rest.getForEntity("/actuator/metrics/" + metric, String.class);
            assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void prometheusEndpointExposesTheCustomBusinessMetrics() throws Exception {
        publishOneValidEvent();
        awaitIncidentProcessing();

        ResponseEntity<String> response = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Prometheus reserves the _created suffix: cbaclean.incidents.created
        // is exposed as cbaclean_incidents_total.
        assertThat(response.getBody()).contains("cbaclean_incidents_total");
        assertThat(response.getBody()).contains("cbaclean_incident_events_processed");
        assertThat(response.getBody()).contains("cbaclean_incident_event_processing_duration");
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

    private void awaitIncidentProcessing() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<String> created =
                    rest.getForEntity("/actuator/metrics/cbaclean.incidents.created", String.class);
            if (created.getStatusCode().is2xxSuccessful() && created.getBody() != null
                    && !created.getBody().contains("\"count\":0")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("incidents.created counter was not incremented in time");
    }
}
