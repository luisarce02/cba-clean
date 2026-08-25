package com.cbclean.report.config;

import com.cbclean.report.application.port.ReportEventPublisher;
import com.cbclean.report.application.report.get.GetReportUseCase;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.repository.ReportRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root: wires the application use case to its ports using the
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
    public SubmitReportUseCase submitReportUseCase(
            ReportRepository reports, ReportEventPublisher events, Clock clock) {
        return new SubmitReportUseCase(reports, events, clock);
    }

    @Bean
    public GetReportUseCase getReportUseCase(ReportRepository reports) {
        return new GetReportUseCase(reports);
    }
}
