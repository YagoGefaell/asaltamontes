package io.github.yagogefaell.asaltamontes.media.service;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;

/** The uploaded file is not an acceptable image (wrong type, corrupt, too small or too big). The message is shown to the user. */
public class InvalidImageException extends BadRequestException {
    public InvalidImageException(String message) {
        super(message);
    }
}
