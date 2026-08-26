package com.cbclean.report.presentation.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Presentation-boundary filter that guarantees every HTTP request is handled
 * with a correlation ID available for logging.
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>An incoming {@code X-Correlation-ID} header with a syntactically valid
 *   value (1-128 characters of letters, digits, dot, dash or underscore - this
 *   includes UUIDs and caller-defined opaque IDs) is preserved.</li>
 *   <li>A missing or malformed header causes a fresh UUID to be generated.</li>
 *   <li>The resolved ID is placed into the SLF4J MDC (key {@code correlationId})
 *   for the duration of the request and removed afterwards, so nothing leaks
 *   between requests on reused container threads.</li>
 *   <li>The resolved ID is echoed back to the caller in the
 *   {@code X-Correlation-ID} response header.</li>
 * </ul>
 *
 * <p>This is the only place where the servlet API meets the correlation ID:
 * domain and application layers stay framework-free and read the value from
 * the logging context (MDC) if they need it.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    /** Accepted correlation ID shape: 1-128 characters of letters, digits and -_. */
    private static final int MAX_LENGTH = 128;
    private static final String VALID_PATTERN = "[A-Za-z0-9._-]+";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = extractOrGenerate(request);
        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String extractOrGenerate(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        if (incoming != null && isValid(incoming.trim())) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }

    private static boolean isValid(String value) {
        return !value.isBlank()
                && value.length() <= MAX_LENGTH
                && value.matches(VALID_PATTERN);
    }
}
