package com.cbclean.report.presentation.correlation;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesACorrelationIdWhenNoHeaderIsPresent() throws Exception {
        AtomicReference<String> duringRequest = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response,
                (request, res) -> duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotNull();
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(duringRequest.get()).isEqualTo(correlationId);
    }

    @Test
    void preservesAnIncomingValidCorrelationId() throws Exception {
        String incoming = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, incoming);
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(duringRequest.get()).isEqualTo(incoming);
    }

    @Test
    void echoesThePreservedCorrelationIdInTheResponse() throws Exception {
        String incoming = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(incoming);
    }

    @Test
    void preservesACustomNonUuidCorrelationId() throws Exception {
        String incoming = "my-custom-correlation-42";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, incoming);
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(duringRequest.get()).isEqualTo(incoming);
    }

    @Test
    void regeneratesTheCorrelationIdWhenTheIncomingHeaderIsMalformed() throws Exception {
        String malformed = "invalid correlation id with spaces!";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, malformed);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String correlationId = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotEqualTo(malformed);
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void clearsTheMdcAfterTheRequestEvenWhenProcessingFails() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() ->
                filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    if (!MDC.getCopyOfContextMap().isEmpty()) {
                        throw new IllegalStateException("boom");
                    }
                    throw new IllegalStateException("boom without correlation context either");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
