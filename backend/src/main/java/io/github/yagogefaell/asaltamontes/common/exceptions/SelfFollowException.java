package io.github.yagogefaell.asaltamontes.common.exceptions;

public class SelfFollowException extends BadRequestException {

    public SelfFollowException() {
        super("You cannot follow yourself");
    }
}