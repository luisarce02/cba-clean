package com.cbclean.incident.infrastructure.messaging;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Externalized retry policy for the {@code incident-service.report-created}
 * consumer.
 *
 * <p>{@code max-retries} is the number of <em>retries after the initial
 * delivery</em>: with the default of 3 a message is delivered up to four times
 * (initial + retry 1..3) before being routed to the DLQ. {@code delays} holds
 * the TTL applied by each successive retry queue; entry {@code i-1} configures
 * the delay before retry number {@code i}. At least {@code max-retries} delays
 * must be configured.</p>
 *
 * <p>Defaults (2s, 4s, 8s) are safe local development values and can be
 * overridden through configuration, e.g. {@code INCIDENT_RETRY_MAX_RETRIES} /
 * {@code INCIDENT_RETRY_DELAYS=2s,4s,8s} in Docker Compose.</p>
 */
@ConfigurationProperties(prefix = "incident.messaging.retry")
public class IncidentMessagingRetryProperties {

    private int maxRetries = 3;

    private List<Duration> delays = List.of(Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8));

    @PostConstruct
    void validate() {
        if (maxRetries < 0) {
            throw new IllegalStateException("incident.messaging.retry.max-retries must be >= 0");
        }
        if (delays == null || delays.size() < maxRetries) {
            throw new IllegalStateException(
                    "incident.messaging.retry.delays needs at least " + maxRetries + " entries, got "
                            + (delays == null ? 0 : delays.size()));
        }
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<Duration> getDelays() {
        return delays;
    }

    public void setDelays(List<Duration> delays) {
        this.delays = delays;
    }
}
