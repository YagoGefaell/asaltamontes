package io.github.yagogefaell.asaltamontes.common.exceptions;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MultipleFieldConflictException extends RuntimeException {

    private final Map<String, String> errors;

    public MultipleFieldConflictException(Map<String, String> errors) {
        super("Multiple field conflicts");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
