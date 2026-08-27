package com.cbclean.incident.config;

import com.cbclean.incident.application.incident.get.GetIncidentUseCase;
import com.cbclean.incident.application.incident.list.GetIncidentsUseCase;
import com.cbclean.incident.application.incident.open.OpenIncidentUseCase;
import com.cbclean.incident.application.incident.status.UpdateIncidentStatusUseCase;
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

    @Bean
    public GetIncidentUseCase getIncidentUseCase(IncidentRepository incidents) {
        return new GetIncidentUseCase(incidents);
    }

    @Bean
    public GetIncidentsUseCase getIncidentsUseCase(IncidentRepository incidents) {
        return new GetIncidentsUseCase(incidents);
    }

    @Bean
    public UpdateIncidentStatusUseCase updateIncidentStatusUseCase(IncidentRepository incidents, Clock clock) {
        return new UpdateIncidentStatusUseCase(incidents, clock);
    }
}
