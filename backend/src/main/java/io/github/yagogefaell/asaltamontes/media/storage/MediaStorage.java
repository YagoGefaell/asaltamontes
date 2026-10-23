package io.github.yagogefaell.asaltamontes.media.storage;

/**
 * Where the image files live. Keys look like {@code avatar/<uuid>/thumb.jpg}. Only implementations know about disks
 * or buckets; the rest of the code works with keys and public URLs (D-004). An S3/R2 implementation can replace
 * the file system one when there is hosting.
 */
public interface MediaStorage {

    void put(String key, byte[] data);

    /** Removes the file if it exists. */
    void delete(String key);

    String publicUrl(String key);
}
