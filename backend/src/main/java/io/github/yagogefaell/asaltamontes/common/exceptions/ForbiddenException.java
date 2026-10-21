package io.github.yagogefaell.asaltamontes.common.exceptions;

/** The user is authenticated but not allowed to do this (403). Mapped in {@link GlobalExceptionHandler}. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
