package com.cbclean.report.presentation.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated response wrapper for report lists")
public record ReportPageResponse(
        @Schema(description = "List of reports on this page")
        List<ReportResponse> content,
        @Schema(description = "Current page number (0-based)")
        int page,
        @Schema(description = "Number of items per page")
        int size,
        @Schema(description = "Total number of elements across all pages")
        long totalElements,
        @Schema(description = "Total number of pages")
        int totalPages
) {
}
