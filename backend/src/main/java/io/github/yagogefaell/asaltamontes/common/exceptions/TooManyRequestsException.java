package io.github.yagogefaell.asaltamontes.common.exceptions;

/** A rate limit or capacity limit was hit (429, with {@code Retry-After}); mapped in {@link GlobalExceptionHandler}. */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
