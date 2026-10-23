package io.github.yagogefaell.asaltamontes.media.storage;

/** The storage backend failed (disk full, no permissions...). Unexpected, so it ends up as a 500. */
public class MediaStorageException extends RuntimeException {
    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
