package com.cbclean.report.presentation.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles authenticated-but-unauthorized requests: responds 403 with the
 * standard JSON error shape without revealing which authority is missing.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        RestSecurityErrorResponseWriter.write(
                request, response, HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to access this resource");
    }
}
