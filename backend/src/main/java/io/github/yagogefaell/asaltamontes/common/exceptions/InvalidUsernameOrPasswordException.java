package io.github.yagogefaell.asaltamontes.common.exceptions;

public class InvalidUsernameOrPasswordException extends RuntimeException {

    public InvalidUsernameOrPasswordException() {
        super("Invalid username or password");
    }
    
}
