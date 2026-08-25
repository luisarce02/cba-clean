package com.cbclean.report.presentation.report;

import com.cbclean.report.application.report.get.GetReportQuery;
import com.cbclean.report.application.report.get.GetReportUseCase;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for the report use cases. Thin by design: it translates HTTP
 * concerns only and delegates all behaviour to the application layer.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final SubmitReportUseCase submitReport;
    private final GetReportUseCase getReport;

    public ReportController(SubmitReportUseCase submitReport, GetReportUseCase getReport) {
        this.submitReport = submitReport;
        this.getReport = getReport;
    }

    @PostMapping
    public ResponseEntity<ReportResponse> submit(@Valid @RequestBody SubmitReportRequest request) {
        Report report = submitReport.execute(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse.from(report));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getById(@PathVariable String id) {
        Report report = getReport.execute(new GetReportQuery(ReportId.fromString(id)));
        return ResponseEntity.ok(ReportResponse.from(report));
    }
}
