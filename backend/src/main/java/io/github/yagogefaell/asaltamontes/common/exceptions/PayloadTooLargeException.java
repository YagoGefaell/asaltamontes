package io.github.yagogefaell.asaltamontes.common.exceptions;

/** The request body is bigger than allowed (413); mapped in {@link GlobalExceptionHandler}. */
public class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
