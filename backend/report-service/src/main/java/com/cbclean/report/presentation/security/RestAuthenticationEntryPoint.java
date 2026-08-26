package com.cbclean.report.presentation.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Entry point for unauthenticated requests (missing, malformed, expired or
 * invalid tokens): responds 401 with the standard JSON error shape and the
 * OAuth2 {@code WWW-Authenticate: Bearer} header. Never reveals why a token
 * was rejected.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        RestSecurityErrorResponseWriter.write(
                request, response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Authentication is required to access this resource");
    }
}
