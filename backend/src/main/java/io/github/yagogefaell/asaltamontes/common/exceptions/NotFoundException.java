package io.github.yagogefaell.asaltamontes.common.exceptions;

/** Resource not found (404). Base class for domain errors of this kind; mapped in {@link GlobalExceptionHandler}. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
