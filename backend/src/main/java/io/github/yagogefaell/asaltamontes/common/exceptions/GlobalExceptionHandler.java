package io.github.yagogefaell.asaltamontes.common.exceptions;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.hibernate.query.sqm.UnknownPathException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.github.yagogefaell.asaltamontes.infrastructure.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex,
                                        HttpServletRequest request) {

        Map<String, String> fields = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (a, b) -> a
                ));

        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Validation error",
                "One or more fields are invalid",
                request,
                fields
        );
    }

    @ExceptionHandler(MultipleFieldConflictException.class)
    public ProblemDetail handleConflict(MultipleFieldConflictException ex,
                                        HttpServletRequest request) {
        String detail = "Errores de validación en uno o más campos";
        if (ex.getErrors() != null && !ex.getErrors().isEmpty()) {
            detail = ex.getErrors().values().iterator().next();
        }
        return buildProblem(
                HttpStatus.CONFLICT,
                "Conflict",
                detail,
                request,
                ex.getErrors()
        );
    }

    @ExceptionHandler(InvalidUsernameOrPasswordException.class)
    public ProblemDetail handleInvalidUsernameOrPassword(InvalidUsernameOrPasswordException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request, null);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req, null);
    }

    // ----- Domain exceptions: each base class covers its subclasses (e.g. AlreadyFollowingException -> 409) -----

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), req, null);
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleDomainConflict(ConflictException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req, null);
    }

    // ----- Spring MVC / Security exceptions that would otherwise end up in the generic 500 -----

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestCookieException.class,
            ConstraintViolationException.class,
            PropertyReferenceException.class
    })
    public ProblemDetail handleMalformedRequest(Exception ex, HttpServletRequest req) {
        log.debug("Malformed request on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return buildProblem(HttpStatus.BAD_REQUEST, "Bad Request", "La petición no es válida", req, null);
    }

    /** An unknown {@code ?sort=} property ends up in the JPQL of @Query methods and Hibernate rejects the path. */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ProblemDetail handleInvalidDataAccessApiUsage(InvalidDataAccessApiUsageException ex, HttpServletRequest req) {
        Throwable cause = ex.getCause();
        while (cause != null && !(cause instanceof UnknownPathException)) {
            cause = cause.getCause();
        }
        if (cause != null) {
            log.warn("Unknown property in query on {} {} (invalid sort?): {}", req.getMethod(), req.getRequestURI(),
                    cause.getMessage());
            return buildProblem(HttpStatus.BAD_REQUEST, "Bad Request", "Parámetro de ordenación no válido", req, null);
        }
        return handleGeneric(ex, req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.NOT_FOUND, "Not Found", "Recurso no encontrado", req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage(), req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type", ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.FORBIDDEN, "Forbidden", "No tienes permiso para hacer esto", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "No autenticado", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Ha ocurrido un error inesperado", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex, 
                                                    HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return buildProblem(HttpStatus.CONFLICT, "Data Integrity Violation", "La operación entra en conflicto con datos existentes", request, null);
    }

    private ProblemDetail buildProblem(@NonNull HttpStatus status, String title, String detail, HttpServletRequest req, Map<String, ?> errors) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setProperty("path", req.getRequestURI());
        problem.setProperty("timestamp", Instant.now());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
