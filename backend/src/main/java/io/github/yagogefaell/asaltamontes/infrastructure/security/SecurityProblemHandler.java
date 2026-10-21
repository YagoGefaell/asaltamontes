package io.github.yagogefaell.asaltamontes.infrastructure.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Errors raised by Spring Security before reaching a controller, as the same ProblemDetail JSON
 * that GlobalExceptionHandler returns: 401 when there is no valid token, 403 when the user lacks permission.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailWriter problemDetailWriter;

    public SecurityProblemHandler(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthFilter.AUTH_ERROR_ATTRIBUTE);
        String detail = reason != null ? reason.toString() : "No autenticado";
        problemDetailWriter.write(response, request, HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        problemDetailWriter.write(response, request, HttpStatus.FORBIDDEN, "Forbidden", "No tienes permiso para hacer esto");
    }
}
