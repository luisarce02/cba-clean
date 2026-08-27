package com.cbclean.incident.presentation.incidents;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateIncidentStatusRequest(
        @Schema(description = "Target status", allowableValues = {"ASSIGNED","IN_PROGRESS","RESOLVED","CANCELLED"}, example = "IN_PROGRESS")
        @NotBlank(message = "status is required")
        String status,

        @Schema(description = "Closing note for RESOLVED/CANCELLED; optional for other transitions")
        String closingNote,

        @Schema(description = "Assignee id for ASSIGNED; optional, defaults to operator")
        String assigneeId) {
}
