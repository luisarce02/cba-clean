package com.cbclean.incident.presentation.incidents;

import com.cbclean.incident.application.incident.get.GetIncidentQuery;
import com.cbclean.incident.application.incident.get.GetIncidentUseCase;
import com.cbclean.incident.application.incident.list.GetIncidentsUseCase;
import com.cbclean.incident.application.incident.status.UpdateIncidentStatusCommand;
import com.cbclean.incident.application.incident.status.UpdateIncidentStatusUseCase;
import com.cbclean.incident.domain.model.DateRange;
import com.cbclean.incident.domain.model.Incident;
import com.cbclean.incident.domain.model.IncidentId;
import com.cbclean.incident.domain.model.IncidentStatus;
import com.cbclean.incident.presentation.GlobalRestExceptionHandler;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Operational incidents derived from citizen reports")
public class IncidentController {

    private final GetIncidentsUseCase getIncidents;
    private final GetIncidentUseCase getIncident;
    private final UpdateIncidentStatusUseCase updateStatus;

    public IncidentController(GetIncidentsUseCase getIncidents,
                              GetIncidentUseCase getIncident,
                              UpdateIncidentStatusUseCase updateStatus) {
        this.getIncidents = getIncidents;
        this.getIncident = getIncident;
        this.updateStatus = updateStatus;
    }

    @Operation(summary = "List incidents", description = "Returns a paginated list of operational incidents, optionally filtered by creation date range. Requires OPERATOR role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of incidents"),
            @ApiResponse(responseCode = "400", description = "Invalid date format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized (requires OPERATOR)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<IncidentPageResponse> list(
            @PageableDefault(size = 10) Pageable pageable,
            @Parameter(description = "Start of date range (ISO 8601, inclusive)", example = "2026-08-31T00:00:00Z")
            @RequestParam(required = false) String from,
            @Parameter(description = "End of date range (ISO 8601, inclusive)", example = "2026-08-31T23:59:59Z")
            @RequestParam(required = false) String to) {
        DateRange dateRange = parseDateRange(from, to);
        Page<Incident> page = getIncidents.execute(pageable, dateRange);
        IncidentPageResponse response = new IncidentPageResponse(
                page.getContent().stream().map(IncidentResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
        return ResponseEntity.ok(response);
    }

    private DateRange parseDateRange(String from, String to) {
        if (from == null && to == null) {
            return null;
        }
        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);
        return DateRange.of(fromInstant, toInstant);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + value + ". Expected ISO 8601 (e.g. 2026-08-31T00:00:00Z)");
        }
    }

    @Operation(summary = "Get an incident by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incident found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = IncidentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Incident not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getById(
            @Parameter(description = "Incident id (UUID)", example = "7f9c24e8-0b5a-4d1e-9f2a-3c6b8d7e1a45")
            @PathVariable String id) {
        Incident incident = getIncident.execute(new GetIncidentQuery(IncidentId.fromString(id)));
        return ResponseEntity.ok(IncidentResponse.from(incident));
    }

    @Operation(summary = "Update incident status", description = "Transitions incident to the requested status following domain workflow: NEW->ASSIGNED->IN_PROGRESS->RESOLVED and CANCELLED from any non-terminal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = IncidentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Illegal transition or validation failure",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Incident not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GlobalRestExceptionHandler.ApiErrorResponse.class)))
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateIncidentStatusRequest request) {
        IncidentStatus target;
        try {
            target = IncidentStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + request.status());
        }
        Incident updated = updateStatus.execute(new UpdateIncidentStatusCommand(
                IncidentId.fromString(id), target, request.closingNote(), request.assigneeId()));
        return ResponseEntity.ok(IncidentResponse.from(updated));
    }
}
