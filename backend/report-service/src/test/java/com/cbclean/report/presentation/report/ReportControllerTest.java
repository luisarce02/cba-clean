package com.cbclean.report.presentation.report;

import com.cbclean.report.application.report.get.ReportNotFoundException;
import com.cbclean.report.application.report.submit.SubmitReportCommand;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import com.cbclean.report.domain.model.GeoLocation;
import com.cbclean.report.domain.model.InvalidReportException;
import com.cbclean.report.domain.model.Report;
import com.cbclean.report.domain.model.ReportType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@org.springframework.context.annotation.Import({
        com.cbclean.report.presentation.security.ReportServiceSecurityConfig.class,
        com.cbclean.report.presentation.security.RestAuthenticationEntryPoint.class,
        com.cbclean.report.presentation.security.RestAccessDeniedHandler.class,
        com.cbclean.report.presentation.security.RolesClaimAuthenticationConverter.class})
@org.springframework.security.test.context.support.WithMockUser(authorities = "ROLE_REPORTER")
class ReportControllerTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitReportUseCase submitReportUseCase;

    @MockitoBean
    private com.cbclean.report.application.report.get.GetReportUseCase getReportUseCase;

    @Test
    void validRequestReturns201CreatedWithGeneratedId() throws Exception {
        UUID id = UUID.randomUUID();
        when(submitReportUseCase.execute(any())).thenReturn(submittedReport(id));

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType": "ILLEGAL_DUMPING",
                                  "description": "Waste dumped next to the river",
                                  "location": {"latitude": 48.2082, "longitude": 16.3738, "address": "Riverside"},
                                  "reporter": {"name": "Jane Doe", "email": "jane@example.com"},
                                  "photoIds": ["photo-1"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("ILLEGAL_DUMPING"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.location.latitude").value(48.2082))
                .andExpect(jsonPath("$.reporter.email").value("jane@example.com"))
                .andExpect(jsonPath("$.photoIds[0]").value("photo-1"));
    }

    @Test
    void useCaseIsInvokedWithMappedCommand() throws Exception {
        UUID id = UUID.randomUUID();
        when(submitReportUseCase.execute(any())).thenReturn(submittedReport(id));

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType": "LITTER",
                                  "description": "  Bags of trash  ",
                                  "location": {"latitude": -45.5, "longitude": 170.5},
                                  "photoIds": []
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<SubmitReportCommand> captor = ArgumentCaptor.forClass(SubmitReportCommand.class);
        verify(submitReportUseCase).execute(captor.capture());
        SubmitReportCommand command = captor.getValue();
        assertThat(command.reportType()).isEqualTo(ReportType.LITTER);
        assertThat(command.description()).isEqualTo("  Bags of trash  ");
        assertThat(command.location().latitude()).isEqualTo(-45.5);
        assertThat(command.location().longitude()).isEqualTo(170.5);
        assertThat(command.reporter()).isNull();
        assertThat(command.photoIds()).isEmpty();
    }

    @Test
    void missingRequiredFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "no type and no location"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reportType')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'location')]").exists());
    }

    @Test
    void outOfRangeCoordinatesReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType": "LITTER",
                                  "location": {"latitude": 91.0, "longitude": 16.3738}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'location.latitude')]").exists());
    }

    @Test
    void invalidReporterContactFormatReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType": "LITTER",
                                  "location": {"latitude": 48.2, "longitude": 16.3},
                                  "reporter": {"email": "not-an-email"}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reporter.email')]").exists());
    }

    @Test
    void unknownEnumValueReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType": "NOT_A_TYPE",
                                  "location": {"latitude": 48.2, "longitude": 16.3}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void domainValidationFailureIsTranslatedTo400() throws Exception {
        when(submitReportUseCase.execute(any()))
                .thenThrow(new InvalidReportException("Description must not exceed 2000 characters"));

        MvcResult result = mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportType": "LITTER",
                                  "location": {"latitude": 48.2, "longitude": 16.3}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Description must not exceed 2000 characters"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("stackTrace");
    }

    @Test
    void responseDoesNotExposePersistenceDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(submitReportUseCase.execute(any())).thenReturn(submittedReport(id));

        MvcResult result = mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("entity", "Entity", "jpa", "Jpa", "hibernate", "persistent", "lazy")
                .contains("\"id\"", "\"type\"", "\"status\"");
    }

    private static String validBody() {
        return """
                {
                  "reportType": "LITTER",
                  "location": {"latitude": 48.2, "longitude": 16.3}
                }
                """;
    }

    @Test
    void existingReportReturns200WithPersistedData() throws Exception {
        UUID id = UUID.randomUUID();
        when(getReportUseCase.execute(any())).thenReturn(submittedReport(id));

        mockMvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value("ILLEGAL_DUMPING"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.description").value("Waste dumped next to the river"))
                .andExpect(jsonPath("$.location.latitude").value(48.2082))
                .andExpect(jsonPath("$.location.longitude").value(16.3738))
                .andExpect(jsonPath("$.reporter.name").value("Jane Doe"))
                .andExpect(jsonPath("$.reporter.email").value("jane@example.com"))
                .andExpect(jsonPath("$.photoIds[0]").value("photo-1"));
    }

    @Test
    void unknownValidIdReturns404WithApiErrorStructure() throws Exception {
        UUID id = UUID.randomUUID();
        when(getReportUseCase.execute(any())).thenThrow(new ReportNotFoundException(
                new com.cbclean.report.domain.model.ReportId(id)));

        MvcResult result = mockMvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("stackTrace");
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Report id must be a valid UUID"));

        verifyNoInteractions(getReportUseCase);
    }

    private static Report submittedReport(UUID id) {
        return Report.submit(
                new com.cbclean.report.domain.model.ReportId(id),
                ReportType.ILLEGAL_DUMPING,
                GeoLocation.of(48.2082, 16.3738),
                new com.cbclean.report.domain.model.Reporter("Jane Doe", "jane@example.com", null),
                "Waste dumped next to the river",
                List.of("photo-1"),
                null,
                NOW);
    }
}
