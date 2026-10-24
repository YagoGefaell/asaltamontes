package io.github.yagogefaell.asaltamontes.media.service;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.media.domain.Media;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.repository.MediaRepository;
import io.github.yagogefaell.asaltamontes.media.storage.MediaStorage;

/**
 * Turns what a request sends as the image of a resource (profile, community, group...) into a reference to one of
 * our own uploads (T17c). A request can carry the media id, or (what the first frontend sent) the URL of the
 * "medium" variant returned by the upload; both are accepted only when the media belongs to the user who is
 * linking it and was uploaded for that kind of resource. The caller has already checked that the user may modify
 * the resource.
 * <p>
 * External image URLs are no longer accepted (BUG-057: every member's browser would call a third party's
 * server, which learns IPs and connection times). The one exception is a resource that already holds a legacy
 * external URL (e.g. the Google profile picture) and receives that same value again, so editing other fields
 * does not break it.
 * <p>
 * The check on URLs is strict on purpose, because what is stored is a string that a browser and nginx will
 * normalize later: it must be exactly an internal URL, optionally with a host in front; and anything with
 * {@code ..}, {@code %}, backslashes or control characters is rejected, so no path trick can point at somebody
 * else's media after validation. The stored value is rebuilt from the media id, never the text received.
 */
@Service
public class MediaReferenceService {

    /** What gets stored: the media (null when the image is removed or is a kept legacy external URL) and the URL. */
    public record Linked(UUID mediaId, String url) {
    }

    private static final String SEGMENTS = String.join("|",
            Arrays.stream(MediaPurpose.values()).map(MediaPurpose::pathSegment).toList());

    /** Case-sensitive and anchored at both ends: nginx and the file system are case-sensitive too. */
    private static final Pattern INTERNAL = Pattern.compile(
            "^(?:https?://[^/\\s?#@\\\\]+)?/media/(" + SEGMENTS + ")/"
                    + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/(?:thumb|medium|large)\\.jpg$");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]");

    private final MediaRepository mediaRepository;
    private final MediaStorage storage;

    public MediaReferenceService(MediaRepository mediaRepository, MediaStorage storage) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
    }

    /**
     * Must run in the transaction that stores the reference: the media row stays share-locked until it commits,
     * so the orphan cleanup cannot delete the image in between (see {@link MediaCleanupService}).
     *
     * @param mediaId         the media to link, or null
     * @param url             used when {@code mediaId} is null: an internal URL, blank to remove the image, or null
     *                        for "no change requested" (the caller should not call this then)
     * @param legacyExternal  the external URL the resource holds today, if any; that same value is kept
     * @throws NotFoundException   the media does not exist or is somebody else's (same answer, ids cannot be probed)
     * @throws BadRequestException wrong purpose, malformed value or a new external URL
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Linked link(UUID mediaId, String url, MediaPurpose expected, Long userId, String legacyExternal) {
        if (mediaId != null) {
            return linkMedia(mediaId, expected, userId);
        }
        if (url == null || url.isBlank()) {
            return new Linked(null, null);
        }
        String value = url.trim();
        if (legacyExternal != null && value.equals(legacyExternal) && !INTERNAL.matcher(value).matches()) {
            // The resource already holds exactly this value (e.g. a Google picture URL, which may contain '%' or
            // '..'): resending the field untouched must keep working. Nothing new is stored.
            return new Linked(null, value);
        }
        if (value.contains("..") || value.contains("%") || value.contains("\\") || CONTROL.matcher(value).find()) {
            throw invalid();
        }
        Matcher m = INTERNAL.matcher(value);
        if (m.matches()) {
            MediaPurpose segmentPurpose = Arrays.stream(MediaPurpose.values())
                    .filter(p -> p.pathSegment().equals(m.group(1))).findFirst().orElseThrow();
            Linked linked = linkMedia(UUID.fromString(m.group(2)), expected, userId);
            if (segmentPurpose != expected) {
                throw invalid();
            }
            return linked;
        }
        throw new BadRequestException("Solo se pueden usar imágenes subidas a la aplicación");
    }

    private Linked linkMedia(UUID mediaId, MediaPurpose expected, Long userId) {
        Media media = mediaRepository.findByIdForShare(mediaId)
                .filter(found -> found.getOwnerId().equals(userId))
                .orElseThrow(() -> new NotFoundException("La imagen no existe"));
        if (media.getPurpose() != expected) {
            throw invalid();
        }
        return new Linked(media.getId(), storage.publicUrl(media.getPurpose().pathSegment() + "/" + media.getId() + "/medium.jpg"));
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Esa imagen no se puede usar aquí");
    }
}
