package com.cbclean.report.presentation.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes security failures as JSON responses shaped like the existing API
 * error contract ({@code status}/{@code error}/{@code message}/{@code timestamp}),
 * so clients see one consistent error format.
 *
 * <p>Messages are intentionally generic: they never reveal why a token was
 * rejected, and no Authorization header or token content is ever logged or
 * echoed.</p>
 */
abstract class RestSecurityErrorResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(RestSecurityErrorResponseWriter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RestSecurityErrorResponseWriter() {
    }

    static void write(HttpServletRequest request,
                      HttpServletResponse response,
                      HttpStatus status,
                      String error,
                      String message) throws IOException {
        log.info("operation=http-security-rejection result=denied status={} path={}",
                status.value(), request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        MAPPER.writeValue(response.getWriter(), body);
    }
}
