package io.github.yagogefaell.asaltamontes.common.exceptions;

public class SelfFollowException extends RuntimeException {

    public SelfFollowException() {
        super("No puedes seguirte a ti mismo");
    }
}