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
    /** Decoded pixels (width x height). Decoding costs ~3 bytes per pixel, and the heap is small (-Xmx512m). */
    private long maxPixels = 40_000_000L;
    private int uploadsPerHour = 30;
    /** Images processed at the same time; the others wait. */
    private int maxConcurrentProcessing = 1;
    private int processingWaitSeconds = 20;
}
