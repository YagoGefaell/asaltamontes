package io.github.yagogefaell.asaltamontes.common.exceptions;

public class AlreadyFollowingException extends ConflictException {

    public AlreadyFollowingException(String username) {
        super("Ya sigues al usuario: " + username);
    }
}