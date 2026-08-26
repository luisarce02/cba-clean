package com.cbclean.report.presentation.correlation;

import com.cbclean.report.application.report.get.GetReportUseCase;
import com.cbclean.report.application.report.submit.SubmitReportUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the HTTP correlation contract end to end through the MVC layer:
 * a missing {@code X-Correlation-ID} is generated, a valid one is preserved,
 * and the resolved ID is always echoed in the response header.
 */
@WebMvcTest(com.cbclean.report.presentation.report.ReportController.class)
class ReportControllerCorrelationTest {

    private static final String VALID_BODY = """
            {
              "reportType": "LITTER",
              "location": {"latitude": 48.2, "longitude": 16.3}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitReportUseCase submitReportUseCase;

    @MockitoBean
    private GetReportUseCase getReportUseCase;

    @Test
    void generatesACorrelationIdWhenTheRequestHasNone() throws Exception {
        MvcResult result = postReport(null);

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotNull();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void preservesAnIncomingCorrelationIdAndEchoesItInTheResponse() throws Exception {
        String incoming = UUID.randomUUID().toString();

        MvcResult result = postReport(incoming);

        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).isEqualTo(incoming);
    }

    @Test
    void generatesANewCorrelationIdWhenTheIncomingValueIsMalformed() throws Exception {
        MvcResult result = postReport("invalid correlation id!");

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotEqualTo("invalid correlation id!");
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void preservesACustomNonUuidCorrelationIdEndToEndThroughHttp() throws Exception {
        MvcResult result = postReport("my-custom-correlation-42");

        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER))
                .isEqualTo("my-custom-correlation-42");
    }

    @Test
    void everyGeneratedCorrelationIdIsUnique() throws Exception {
        String first = postReport(null).getResponse().getHeader(CorrelationIdFilter.HEADER);
        String second = postReport(null).getResponse().getHeader(CorrelationIdFilter.HEADER);

        assertThat(first).isNotEqualTo(second);
    }

    private MvcResult postReport(String correlationId) throws Exception {
        var requestBuilder = post("/api/v1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY);
        if (correlationId != null) {
            requestBuilder.header(CorrelationIdFilter.HEADER, correlationId);
        }
        when(submitReportUseCase.execute(any())).thenReturn(
                com.cbclean.report.domain.model.Report.submit(
                        new com.cbclean.report.domain.model.ReportId(UUID.randomUUID()),
                        com.cbclean.report.domain.model.ReportType.LITTER,
                        com.cbclean.report.domain.model.GeoLocation.of(48.2, 16.3),
                        null, null, java.util.List.of(), null,
                        java.time.Instant.parse("2026-08-25T12:00:00Z")));
        return mockMvc.perform(requestBuilder).andExpect(status().isCreated()).andReturn();
    }
}
