package com.cbclean.incident.config;

import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.domain.repository.IncidentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root: wires the application use cases to their ports using the
 * infrastructure adapters. Keeps application and domain layers free of
 * Spring annotations.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenIncidentUseCase openIncidentUseCase(IncidentRepository incidents, Clock clock) {
        return new OpenIncidentUseCase(incidents, clock);
    }
}
