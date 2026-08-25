package com.cbclean.report.presentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the Report Service HTTP API. Presentation-layer
 * concern only: it describes the REST contract and touches nothing else.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reportServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CBA Clean Report Service")
                .description("Handles citizen waste reports for the CBA Clean platform: "
                        + "citizens can submit waste reports (litter, illegal dumping, overflowing bins, "
                        + "bulky waste) with a location, optional description, reporter contact details "
                        + "and photo references, and look reports up by id.")
                .version("1.0.0"));
    }
}
