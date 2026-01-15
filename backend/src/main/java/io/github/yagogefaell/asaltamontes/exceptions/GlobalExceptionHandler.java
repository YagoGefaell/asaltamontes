package io.github.yagogefaell.asaltamontes.exceptions;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MultipleFieldConflictException.class)
    public ResponseEntity<Map<String, String>> handleMultipleConflicts(MultipleFieldConflictException ex) {
        return ResponseEntity.status(409).body(ex.getErrors());
    }
}
