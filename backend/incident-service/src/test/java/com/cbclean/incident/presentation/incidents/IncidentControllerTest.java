package com.cbclean.incident.presentation.incidents;

import com.cbclean.incident.application.incident.get.GetIncidentUseCase;
import com.cbclean.incident.application.incident.get.IncidentNotFoundException;
import com.cbclean.incident.application.incident.list.GetIncidentsUseCase;
import com.cbclean.incident.application.incident.status.UpdateIncidentStatusUseCase;
import com.cbclean.incident.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
@Import({
        com.cbclean.incident.infrastructure.security.IncidentServiceSecurityConfig.class
})
@org.springframework.security.test.context.support.WithMockUser(authorities = "ROLE_OPERATOR")
class IncidentControllerTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetIncidentsUseCase getIncidentsUseCase;

    @MockitoBean
    private GetIncidentUseCase getIncidentUseCase;

    @MockitoBean
    private UpdateIncidentStatusUseCase updateStatusUseCase;

    private Incident sampleIncident(IncidentStatus status) {
        Incident inc = Incident.open(IncidentId.newId(),
                ReportId.fromString("11111111-1111-1111-1111-111111111111"),
                IncidentType.LITTER, IncidentLocation.of(10, 20), "desc", IncidentPriority.HIGH, NOW);
        // transition if needed for test
        if (status == IncidentStatus.ASSIGNED) {
            inc.assign(Assignment.to("op-1", NOW), NOW);
        } else if (status == IncidentStatus.IN_PROGRESS) {
            inc.assign(Assignment.to("op-1", NOW), NOW);
            inc.startWork(NOW);
        } else if (status == IncidentStatus.RESOLVED) {
            inc.assign(Assignment.to("op-1", NOW), NOW);
            inc.startWork(NOW);
            inc.resolve("done", NOW);
        }
        return inc;
    }

    @Test
    void listReturns200() throws Exception {
        Incident inc = sampleIncident(IncidentStatus.NEW);
        when(getIncidentsUseCase.execute(any(), any())).thenReturn(
                new PageImpl<>(List.of(inc), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.content[0].type").value("LITTER"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listWithDateRangeReturns200() throws Exception {
        Incident inc = sampleIncident(IncidentStatus.NEW);
        when(getIncidentsUseCase.execute(any(), any())).thenReturn(
                new PageImpl<>(List.of(inc), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/incidents")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("NEW"));
    }

    @Test
    void listWithInvalidDateFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                        .param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWithFromAfterToReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/incidents")
                        .param("from", "2026-08-31T00:00:00Z")
                        .param("to", "2026-08-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturns200() throws Exception {
        Incident inc = sampleIncident(IncidentStatus.NEW);
        when(getIncidentUseCase.execute(any())).thenReturn(inc);

        mockMvc.perform(get("/api/v1/incidents/{id}", inc.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inc.id().value().toString()));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getIncidentUseCase.execute(any())).thenThrow(new IncidentNotFoundException(new IncidentId(id)));

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchStatusReturns200() throws Exception {
        Incident inc = sampleIncident(IncidentStatus.NEW);
        // keep same id for comparison
        Incident updatedWithSameId = Incident.reconstitute(inc.id(), inc.reportId(), inc.type(), inc.location(), inc.description(),
                IncidentStatus.ASSIGNED, inc.priority(), Assignment.to("op-1", NOW), null, inc.createdAt(), NOW);
        when(updateStatusUseCase.execute(any())).thenReturn(updatedWithSameId);

        mockMvc.perform(patch("/api/v1/incidents/{id}/status", inc.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ASSIGNED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void patchWithUnknownStatusReturns400() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"NOT_A_STATUS"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchIllegalTransitionReturns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(updateStatusUseCase.execute(any())).thenThrow(new IllegalStateException("Illegal status transition"));

        mockMvc.perform(patch("/api/v1/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RESOLVED"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
