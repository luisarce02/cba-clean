package com.cbclean.report.presentation.report;

import com.cbclean.report.application.report.get.GetReportQuery;
import com.cbclean.report.application.report.get.GetReportUseCase;
import com.cbclean.report.application.report.list.ListReportsUseCase;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportId;
import com.cbclean.report.presentation.GlobalRestExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(name = "Reports", description = "Citizen waste reports")
public class ReportController {

    private final SubmitReportUseCase submitReport;
    private final GetReportUseCase getReport;
    private final ListReportsUseCase listReports;

    public ReportController(SubmitReportUseCase submitReport, GetReportUseCase getReport, ListReportsUseCase listReports) {
        this.submitReport = submitReport;
        this.getReport = getReport;
        this.listReports = listReports;
    }

    @Operation(
            summary = "Submit a new waste report",
            description = "Creates a new waste report for the given location. The report starts in "
                    + "RECEIVED status. Only the location is mandatory; reporter details are optional "
                    + "so reports can be submitted anonymously.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Report created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation failure, "
                    + "unknown report type or malformed body)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ReportResponse> submit(@Valid @RequestBody SubmitReportRequest request) {
        Report report = submitReport.execute(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReportResponse.from(report));
    }

    @Operation(summary = "List waste reports",
            description = "Returns a paginated list of waste reports. Requires OPERATOR role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of reports",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReportPageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized (requires OPERATOR)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ReportPageResponse> list(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<Report> page = listReports.execute(pageable);
        ReportPageResponse response = new ReportPageResponse(
                page.getContent().stream().map(ReportResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get a waste report by id",
            description = "Returns the waste report with the given id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Id is not a valid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No report exists with the given id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getById(
            @Parameter(description = "Unique identifier of the report (UUID)", example = "7f9c24e8-0b5a-4d1e-9f2a-3c6b8d7e1a45")
            @PathVariable String id) {
        Report report = getReport.execute(new GetReportQuery(ReportId.fromString(id)));
        return ResponseEntity.ok(ReportResponse.from(report));
    }
}

