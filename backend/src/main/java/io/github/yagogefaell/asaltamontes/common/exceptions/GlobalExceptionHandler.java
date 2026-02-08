package io.github.yagogefaell.asaltamontes.common.exceptions;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

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
                        (a, b) -> a // en caso de conflicto, se queda con el primero
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

        return buildProblem(
                HttpStatus.CONFLICT,
                "Conflict",
                "Duplicate values detected",
                request,
                ex.getErrors()
        );
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), request, null);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req, null);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest req) {
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", req, null);
    }

    private ProblemDetail buildProblem(HttpStatus status, String title, String detail, HttpServletRequest req, Map<String, ?> errors) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setProperty("path", req.getRequestURI());
        problem.setProperty("timestamp", Instant.now());
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
