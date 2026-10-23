package io.github.yagogefaell.asaltamontes.media.service;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.github.yagogefaell.asaltamontes.common.exceptions.PayloadTooLargeException;
import io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.RateLimiter;
import io.github.yagogefaell.asaltamontes.media.MediaProperties;
import io.github.yagogefaell.asaltamontes.media.domain.Media;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.domain.MediaStatus;
import io.github.yagogefaell.asaltamontes.media.dto.MediaDto;
import io.github.yagogefaell.asaltamontes.media.dto.MediaUrlsDto;
import io.github.yagogefaell.asaltamontes.media.repository.MediaRepository;
import io.github.yagogefaell.asaltamontes.media.storage.MediaStorage;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.extern.slf4j.Slf4j;

/**
 * Uploads: limits per user, validation and re-encoding ({@link ImageProcessor}), storage and metadata.
 * Not transactional on purpose: no database connection is held while an image is being processed.
 */
@Slf4j
@Service
public class MediaService {

    private static final Duration UPLOAD_PERIOD = Duration.ofHours(1);

    private final MediaProperties properties;
    private final ImageProcessor imageProcessor;
    private final MediaStorage storage;
    private final MediaRepository mediaRepository;
    private final RateLimiter rateLimiter;
    private final Semaphore processing;

    public MediaService(MediaProperties properties, ImageProcessor imageProcessor, MediaStorage storage,
                        MediaRepository mediaRepository, RateLimiter rateLimiter) {
        this.properties = properties;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
        this.mediaRepository = mediaRepository;
        this.rateLimiter = rateLimiter;
        // Decoding a big photo takes a lot of heap: a few at a time, the others wait
        this.processing = new Semaphore(properties.getMaxConcurrentProcessing());
    }

    public MediaDto upload(UserAccount owner, MediaPurpose purpose, MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageException("No se ha enviado ninguna imagen");
        }
        if (file.getSize() > properties.getMaxBytes()) {
            throw new PayloadTooLargeException("La imagen es demasiado grande (máximo "
                    + properties.getMaxBytes() / (1024 * 1024) + " MB)");
        }
        long retryAfter = rateLimiter.tryAcquire("media:user:" + owner.getId(), properties.getUploadsPerHour(), UPLOAD_PERIOD);
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Has subido demasiadas imágenes. Vuelve a probar en "
                    + Math.max(1, retryAfter / 60) + " minutos", retryAfter);
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("No se ha podido leer el archivo");
        }

        ImageProcessor.Processed processed = processLimited(data);

        UUID id = UUID.randomUUID();
        String prefix = purpose.pathSegment() + "/" + id + "/";
        String[] keys = {prefix + "thumb.jpg", prefix + "medium.jpg", prefix + "large.jpg"};
        try {
            storage.put(keys[0], processed.thumb());
            storage.put(keys[1], processed.medium());
            storage.put(keys[2], processed.large());

            Media media = new Media();
            media.setId(id);
            media.setOwnerId(owner.getId());
            media.setPurpose(purpose);
            media.setStatus(MediaStatus.READY);
            media.setWidth(processed.width());
            media.setHeight(processed.height());
            media.setBytes(processed.totalBytes());
            mediaRepository.save(media);
        } catch (RuntimeException e) {
            deleteQuietly(keys);
            throw e;
        }
        return new MediaDto(id, new MediaUrlsDto(
                storage.publicUrl(keys[0]), storage.publicUrl(keys[1]), storage.publicUrl(keys[2])));
    }

    private ImageProcessor.Processed processLimited(byte[] data) {
        boolean acquired;
        try {
            acquired = processing.tryAcquire(properties.getProcessingWaitSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TooManyRequestsException("El servidor está ocupado; inténtalo de nuevo en unos segundos", 5);
        }
        if (!acquired) {
            throw new TooManyRequestsException("El servidor está ocupado; inténtalo de nuevo en unos segundos", 5);
        }
        try {
            return imageProcessor.process(data);
        } finally {
            processing.release();
        }
    }

    private void deleteQuietly(String... keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Could not clean up {} after a failed upload", key, e);
            }
        }
    }
}
