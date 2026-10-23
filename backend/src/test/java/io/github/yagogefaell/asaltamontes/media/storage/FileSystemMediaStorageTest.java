package io.github.yagogefaell.asaltamontes.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.yagogefaell.asaltamontes.media.MediaProperties;

class FileSystemMediaStorageTest {

    @TempDir Path dir;

    private FileSystemMediaStorage storage() {
        MediaProperties properties = new MediaProperties();
        properties.setDir(dir.resolve("media").toString());
        properties.setPublicBaseUrl("/media/");
        return new FileSystemMediaStorage(properties);
    }

    @Test
    void putsAndDeletesFiles_andBuildsPublicUrls() throws Exception {
        FileSystemMediaStorage storage = storage();

        storage.put("avatar/abc/thumb.jpg", new byte[] {1, 2, 3});

        Path file = dir.resolve("media/avatar/abc/thumb.jpg");
        assertThat(Files.readAllBytes(file)).containsExactly(1, 2, 3);
        assertThat(storage.publicUrl("avatar/abc/thumb.jpg")).isEqualTo("/media/avatar/abc/thumb.jpg");
        try (var siblings = Files.list(file.getParent())) {
            assertThat(siblings.map(p -> p.getFileName().toString())).containsExactly("thumb.jpg"); // no temp file left
        }

        storage.delete("avatar/abc/thumb.jpg");

        assertThat(file).doesNotExist();
        assertThat(file.getParent()).doesNotExist();
        storage.delete("avatar/abc/thumb.jpg"); // already gone: not an error
    }

    @Test
    void neverLeavesTheStorageFolder() {
        FileSystemMediaStorage storage = storage();

        assertThatThrownBy(() -> storage.put("../escape.jpg", new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.put("avatar/../../escape.jpg", new byte[] {1})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThat(dir.resolve("escape.jpg")).doesNotExist();
    }
}
