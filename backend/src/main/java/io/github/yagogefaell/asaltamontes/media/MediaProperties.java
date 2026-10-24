package io.github.yagogefaell.asaltamontes.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** Image upload settings (asaltamontes.media.*). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "asaltamontes.media")
public class MediaProperties {

    /** Folder of the file system storage (the {@code media_data} volume). */
    private String dir = "/data/media";
    /** Prefix of the public URLs; nginx serves it straight from the volume. */
    private String publicBaseUrl = "/media";
    private long maxBytes = 10L * 1024 * 1024;
    private int minSide = 64;
    private int maxSide = 8000;
    /**
     * Decoded pixels (width x height). Decoding costs 3-4 bytes per pixel (a PNG is 4), on top of the 10 MB upload,
     * and the heap is small (-Xmx512m). The largest size served is 1280 px, so phone photos (12-24 Mpx) are plenty.
     */
    private long maxPixels = 24_000_000L;
    private int uploadsPerHour = 30;
    /** Images processed at the same time; the others wait (holding no upload bytes in memory). */
    private int maxConcurrentProcessing = 1;
    /** Total size of the stored images one user may have (sum of the variants; orphans count until cleaned up). */
    private org.springframework.util.unit.DataSize maxBytesPerUser = org.springframework.util.unit.DataSize.ofMegabytes(50);
    /** Images that no resource references are deleted once they are older than this (must stay well above 0). */
    private java.time.Duration orphanGracePeriod = java.time.Duration.ofHours(24);
    /** Orphans handled per query in each cleanup run. */
    private int cleanupBatchSize = 100;
    /** How long a request waits for a processing slot before being rejected with 429. */
    private int processingWaitSeconds = 3;
}
