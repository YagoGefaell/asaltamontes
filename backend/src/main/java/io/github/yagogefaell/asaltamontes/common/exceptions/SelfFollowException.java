package io.github.yagogefaell.asaltamontes.common.exceptions;

public class SelfFollowException extends RuntimeException {

    public SelfFollowException() {
        super("You cannot follow yourself");
    }
}