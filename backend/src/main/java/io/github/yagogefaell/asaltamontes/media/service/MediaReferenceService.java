package io.github.yagogefaell.asaltamontes.media.service;

import java.util.Arrays;
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
 * <p>
 * The check is strict on purpose, because what is stored is a string that a browser and nginx will normalize
 * later: relative values must be exactly an internal URL; absolute http(s) values are external unless they
 * contain {@code /media/}, in which case they must be exactly an internal URL with a host; and anything with
 * {@code ..}, {@code %}, backslashes or control characters is rejected, so no path trick can point at
 * somebody else's media after validation.
 * The caller has already checked that the user may modify the resource.
 */
@Service
public class MediaReferenceService {

    private static final String SEGMENTS = String.join("|",
            Arrays.stream(MediaPurpose.values()).map(MediaPurpose::pathSegment).toList());

    /** Case-sensitive and anchored at both ends: nginx and the file system are case-sensitive too. */
    private static final Pattern INTERNAL = Pattern.compile(
            "^(?:https?://[^/\\s?#@\\\\]+)?/media/(" + SEGMENTS + ")/"
                    + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/(?:thumb|medium|large)\\.jpg$");
    private static final Pattern ABSOLUTE = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\s]");

    private final MediaRepository mediaRepository;

    public MediaReferenceService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    /** True when the value is exactly a URL of our own media storage. */
    public static boolean isInternal(String url) {
        return url != null && INTERNAL.matcher(url.trim()).matches();
    }

    /**
     * Fails unless {@code url} is empty, a harmless external URL, or a media of {@code userId} with the expected
     * purpose. A missing media and someone else's media get the same 404 so ids cannot be probed.
     */
    public void checkLinkable(String url, MediaPurpose expected, Long userId) {
        if (url == null || url.isBlank()) {
            return;
        }
        String value = url.trim();
        if (value.contains("..") || value.contains("%") || value.contains("\\") || CONTROL.matcher(value).find()) {
            throw invalid();
        }
        boolean absolute = ABSOLUTE.matcher(value).matches();
        if (absolute && !value.toLowerCase().contains("/media/")) {
            return; // external image
        }
        Matcher m = INTERNAL.matcher(value);
        if (!m.matches()) {
            throw invalid(); // relative values must be an internal URL; absolute ones that mention /media/ too
        }
        MediaPurpose segmentPurpose = Arrays.stream(MediaPurpose.values())
                .filter(p -> p.pathSegment().equals(m.group(1))).findFirst().orElseThrow();
        Media media = mediaRepository.findById(UUID.fromString(m.group(2)))
                .filter(found -> found.getOwnerId().equals(userId))
                .orElseThrow(() -> new NotFoundException("La imagen no existe"));
        if (media.getPurpose() != expected || segmentPurpose != expected) {
            throw invalid();
        }
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Esa imagen no se puede usar aquí");
    }
}
