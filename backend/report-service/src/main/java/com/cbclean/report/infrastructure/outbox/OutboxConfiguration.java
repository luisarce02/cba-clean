package com.cbclean.report.infrastructure.outbox;

import com.cbclean.report.config.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Infrastructure configuration for the transactional outbox: enables
 * scheduling for {@link OutboxPublisher} and binds the externalized
 * {@link OutboxProperties}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {
}
