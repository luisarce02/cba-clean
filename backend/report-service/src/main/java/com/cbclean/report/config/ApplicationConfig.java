package com.cbclean.report.config;

import com.cbclean.report.application.port.OutboxStore;
import com.cbclean.report.application.port.ReportMetrics;
import com.cbclean.report.application.port.UnitOfWork;
import com.cbclean.report.application.report.get.GetReportUseCase;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.repository.ReportRepository;
import com.cbclean.report.infrastructure.transaction.SpringUnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

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
    public UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new SpringUnitOfWork(transactionManager);
    }

    @Bean
    public SubmitReportUseCase submitReportUseCase(
            ReportRepository reports, OutboxStore outbox, UnitOfWork unitOfWork,
            Clock clock, ReportMetrics metrics) {
        return new SubmitReportUseCase(reports, outbox, unitOfWork, clock, metrics);
    }

    @Bean
    public GetReportUseCase getReportUseCase(ReportRepository reports) {
        return new GetReportUseCase(reports);
    }
}
