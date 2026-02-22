package io.github.yagogefaell.asaltamontes.common.exceptions;

public class AlreadyFollowingException extends RuntimeException {

    public AlreadyFollowingException(String username) {
        super("Ya sigues al usuario: " + username);
    }
}