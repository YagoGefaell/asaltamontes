package io.github.yagogefaell.asaltamontes.media.service;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.media.domain.Media;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.repository.MediaRepository;

/**
 * Guards the places where a resource (profile, community, group...) stores an image URL (T17b).
 * Until T17c replaces URLs with media ids, a field that holds a URL of one of our own uploads
 * ({@code /media/<purpose>/<uuid>/<variant>.jpg}) is a reference to that media, so it is only accepted when
 * the media belongs to the user who is linking it and was uploaded for that kind of resource.
 * External URLs are not touched here. The caller has already checked that the user may modify the resource.
 */
@Service
public class MediaReferenceService {

    private static final Pattern INTERNAL = Pattern.compile(
            "(?i)^(?:https?://[^/\\s]+)?/media/([a-z-]+)/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/");

    private final MediaRepository mediaRepository;

    public MediaReferenceService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    /** True when the value points at our own media storage (so it needs ownership checks). */
    public static boolean isInternal(String url) {
        return url != null && INTERNAL.matcher(url.trim()).find();
    }

    /**
     * Fails unless {@code url} is external/empty or a media of {@code userId} with the expected purpose.
     * A missing media and someone else's media get the same 404 so ids cannot be probed.
     */
    public void checkLinkable(String url, MediaPurpose expected, Long userId) {
        if (url == null) {
            return;
        }
        Matcher m = INTERNAL.matcher(url.trim());
        if (!m.find()) {
            return;
        }
        Optional<MediaPurpose> segmentPurpose = Arrays.stream(MediaPurpose.values())
                .filter(p -> p.pathSegment().equalsIgnoreCase(m.group(1))).findFirst();
        UUID id = UUID.fromString(m.group(2));
        Media media = mediaRepository.findById(id)
                .filter(found -> found.getOwnerId().equals(userId))
                .orElseThrow(() -> new NotFoundException("La imagen no existe"));
        if (media.getPurpose() != expected || segmentPurpose.orElse(null) != expected) {
            throw new BadRequestException("Esa imagen no se puede usar aquí");
        }
    }
}
