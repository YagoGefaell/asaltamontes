package io.github.yagogefaell.asaltamontes.media.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.media.MediaProperties;

/** Stores the files under {@code asaltamontes.media.dir}; nginx serves that folder as {@code /media/}. */
@Component
public class FileSystemMediaStorage implements MediaStorage {

    private final Path root;
    private final String publicBaseUrl;

    public FileSystemMediaStorage(MediaProperties properties) {
        this.root = Path.of(properties.getDir()).toAbsolutePath().normalize();
        this.publicBaseUrl = properties.getPublicBaseUrl().replaceAll("/+$", "");
    }

    @Override
    public void put(String key, byte[] data) {
        Path target = resolve(key);
        Path tmp = target.resolveSibling(".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            Files.write(tmp, data);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // Nothing else to do: the original error is the one that matters
            }
            throw new MediaStorageException("Could not store " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(target.getParent()); // the per-image folder, once its last variant is gone
        } catch (DirectoryNotEmptyException ignored) {
            // Other variants still there
        } catch (IOException e) {
            throw new MediaStorageException("Could not delete " + key, e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    /** Keys are built by this application, but a key can never leave the storage folder anyway. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
