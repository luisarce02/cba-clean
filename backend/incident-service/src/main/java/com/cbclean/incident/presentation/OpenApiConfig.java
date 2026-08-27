package com.cbclean.incident.presentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI incidentServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CBA Clean Incident Service")
                .description("Operational incident management for the CBA Clean platform: incidents are created from citizen reports via RabbitMQ and managed by OPERATOR users (list, view, status transitions).")
                .version("1.0.0"));
    }
}
