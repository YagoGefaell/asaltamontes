package io.github.yagogefaell.asaltamontes.common.exceptions;

/** Request conflicts with the current state (409). Base class for domain errors of this kind; mapped in {@link GlobalExceptionHandler}. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
