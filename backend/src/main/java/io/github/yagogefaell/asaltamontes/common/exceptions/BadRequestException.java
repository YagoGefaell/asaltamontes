package io.github.yagogefaell.asaltamontes.common.exceptions;

/** Invalid request (400). Base class for domain errors of this kind; mapped in {@link GlobalExceptionHandler}. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
