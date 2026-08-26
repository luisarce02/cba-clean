package com.cbclean.report.presentation.security;

import com.cbclean.report.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the complete HTTP security chain of the Report Service against real
 * PostgreSQL/RabbitMQ containers, using signed JWTs validated against an
 * in-memory RSA key pair (no external identity provider).
 *
 * <p>Covers the full authentication/authorization matrix, public vs protected
 * actuator endpoints, 404 preservation for unknown paths, statelessness,
 * correlation ID propagation through authenticated requests, and ensures
 * security failures never leak token material into logs.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "cbaclean.outbox.poll-interval=PT1H"
        })
@AutoConfigureObservability
@Testcontainers
class ReportApiSecurityIntegrationTest {

    private static final String VALID_BODY = """
            {
              "reportType": "LITTER",
              "description": "Bags of trash near the park",
              "location": {"latitude": 48.2, "longitude": 16.3}
            }
            """;
    /** Distinctive marker used to prove no Authorization header value leaks into logs. */
    private static final String TOKEN_LEAK_MARKER = "LEAKMARKER-7f3a9";

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * Replaces the production JwtDecoder with one validating against a locally
     * generated RSA key pair so tests can mint real signed tokens.
     */
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

    private String localPortBase() {
        return "http://localhost:" + environment.getProperty("local.server.port");
    }

    private ResponseEntity<String> exchange(HttpMethod method,
                                            String path,
                                            String bearerToken,
                                            String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return rest.exchange(localPortBase() + path, method, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void postWithoutTokenIsUnauthorized() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports", null, VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("\"status\":401");
    }

    @Test
    void getWithoutTokenIsUnauthorized() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/reports/" + java.util.UUID.randomUUID(),
                null, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tamperedTokenSignatureIsRejectedAsUnauthorized() {
        String token = TestJwts.token("attacker", List.of("REPORTER"));
        // Corrupt the signature segment.
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports", tampered, VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsUnauthorized() {
        String expired = TestJwts.token("reporter-1", List.of("REPORTER"),
                Instant.now().minusSeconds(60));

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports", expired, VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedTokenIsUnauthorized() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports", "not-a-jwt", VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validReporterTokenCanCreateAReport() {
        String token = TestJwts.token("citizen-jane", List.of("REPORTER"));

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports", token, VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"type\":\"LITTER\"");
        // Authentication must not create HTTP sessions.
        assertThat(response.getHeaders().get("Set-Cookie")).isNull();
    }

    @Test
    void validReporterTokenCanRetrieveAReport() throws Exception {
        String token = TestJwts.token("citizen-jane", List.of("REPORTER"));
        String created = exchange(HttpMethod.POST, "/api/v1/reports", token, VALID_BODY).getBody();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).at("/id").asText();

        ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/v1/reports/" + id, token, null);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).contains("Bags of trash near the park");
        assertThat(response.getHeaders().get("Set-Cookie")).isNull();
    }

    @Test
    void validOperatorTokenCanRetrieveButNotCreateReports() throws Exception {
        String operator = TestJwts.token("operator-1", List.of("OPERATOR"));
        String reporter = TestJwts.token("citizen-jane", List.of("REPORTER"));

        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/v1/reports", reporter, VALID_BODY);
        String id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getBody()).at("/id").asText();

        ResponseEntity<String> read = exchange(HttpMethod.GET, "/api/v1/reports/" + id, operator, null);
        ResponseEntity<String> write = exchange(HttpMethod.POST, "/api/v1/reports", operator, VALID_BODY);

        assertThat(read.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(write.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void validTokenWithoutRequiredAuthorityIsForbidden() {
        // Authenticated, but carries neither REPORTER nor OPERATOR.
        String token = TestJwts.token("someone-else", List.of("SOMEONE"));

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports", token, VALID_BODY);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).doesNotContain("REPORTER");
    }

    @Test
    void healthEndpointRemainsPublic() {
        ResponseEntity<String> response = rest.getForEntity(localPortBase() + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void infoEndpointRemainsPublic() {
        ResponseEntity<String> response = rest.getForEntity(localPortBase() + "/actuator/info", String.class);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
    }

    @Test
    void metricsAndPrometheusAreNotPubliclyAccessible() {
        ResponseEntity<String> metrics = rest.getForEntity(localPortBase() + "/actuator/metrics", String.class);
        ResponseEntity<String> prometheus =
                rest.getForEntity(localPortBase() + "/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThat(prometheus.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    @Test
    void operatorCanAccessMetricsAndPrometheus() {
        String operator = TestJwts.token("operator-1", List.of("OPERATOR"));

        ResponseEntity<String> metrics = exchange(HttpMethod.GET, "/actuator/metrics", operator, null);
        ResponseEntity<String> prometheus = exchange(HttpMethod.GET, "/actuator/prometheus", operator, null);

        assertThat(metrics.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(prometheus.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(prometheus.getBody()).contains("cbaclean_reports_total");
    }

    @Test
    void sensitiveActuatorEndpointsRemainUnavailable() {
        String operator = TestJwts.token("operator-1", List.of("OPERATOR"));
        for (String endpoint : new String[]{
                "env", "beans", "configprops", "mappings", "heapdump", "threaddump", "loggers"}) {
            ResponseEntity<String> response =
                    exchange(HttpMethod.GET, "/actuator/" + endpoint, operator, null);
            assertThat(response.getStatusCode())
                    .as("%s must not be exposed", endpoint)
                    .isNotEqualTo(org.springframework.http.HttpStatus.OK);
        }
    }

    @Test
    void unknownEndpointsKeepReturning404InsteadOf500() {
        String reporter = TestJwts.token("citizen-jane", List.of("REPORTER"));

        ResponseEntity<String> unknownApi =
                exchange(HttpMethod.GET, "/api/v1/definitely-not-a-resource", reporter, null);
        ResponseEntity<String> unknownAnonymous =
                rest.getForEntity(localPortBase() + "/no/such/path", String.class);

        assertThat(unknownApi.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(unknownAnonymous.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void domainValidationStillAppliesAfterAuthentication() {
        String token = TestJwts.token("citizen-jane", List.of("REPORTER"));

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports", token, """
                {"reportType": "LITTER", "location": {"latitude": 91.0, "longitude": 16.3}}
                """);

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("latitude");
    }

    @Test
    void correlationIdBehaviorSurvivesAuthentication() {
        String token = TestJwts.token("citizen-jane", List.of("REPORTER"));

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/reports",
                token, VALID_BODY, "e2e-auth-correlation-42");

        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("e2e-auth-correlation-42");
    }

    @Test
    void securityFailuresDoNotLeakAuthorizationHeaderValuesIntoLogs() throws Exception {
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var listAppender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        listAppender.start();
        rootLogger.addAppender(listAppender);
        try {
            exchange(HttpMethod.POST, "/api/v1/reports",
                    "forged." + TOKEN_LEAK_MARKER + ".signature", VALID_BODY);
            exchange(HttpMethod.POST, "/api/v1/reports", null, VALID_BODY);
        } finally {
            rootLogger.detachAppender(listAppender);
            listAppender.stop();
        }

        List<String> logLines = listAppender.list.stream()
                .map(event -> event.getFormattedMessage())
                .toList();
        assertThat(logLines).allSatisfy(line ->
                assertThat(line).doesNotContain(TOKEN_LEAK_MARKER));
        assertThat(logLines).noneMatch(line ->
                line.toLowerCase().contains("authorization"));
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String bearerToken,
                                            String body, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", correlationId);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return rest.exchange(localPortBase() + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
