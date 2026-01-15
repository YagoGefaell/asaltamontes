package io.github.yagogefaell.asaltamontes.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Manejo de errores de validación (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
          .getFieldErrors()
          .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return errors;
    }

    // Manejo de conflictos de registro (usuarios duplicados)
    @ExceptionHandler(MultipleFieldConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleMultipleFieldConflict(MultipleFieldConflictException ex) {
        return ex.getErrors();
    }
}
