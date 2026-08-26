package com.cbclean.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Operational settings of the transactional outbox. All values are
 * externalized (prefix {@code cbaclean.outbox}) with safe local defaults.
 *
 * @param pollInterval          delay between outbox polling rounds; also drives
 *                              how quickly a failed publication is retried
 * @param batchSize             maximum number of events claimed per round
 * @param publishConfirmTimeout how long to wait for the RabbitMQ publisher
 *                              confirm before treating an attempt as failed
 */
@ConfigurationProperties(prefix = "cbaclean.outbox")
public record OutboxProperties(
        @DefaultValue("PT5S") Duration pollInterval,
        @DefaultValue("20") int batchSize,
        @DefaultValue("PT5S") Duration publishConfirmTimeout) {

    public OutboxProperties {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("outbox poll-interval must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("outbox batch-size must be greater than zero");
        }
        if (publishConfirmTimeout == null || publishConfirmTimeout.isNegative() || publishConfirmTimeout.isZero()) {
            throw new IllegalArgumentException("outbox publish-confirm-timeout must be positive");
        }
    }
}
