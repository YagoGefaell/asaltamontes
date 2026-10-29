package io.github.yagogefaell.asaltamontes.media.domain;

/** What an uploaded image is for. {@link #pathSegment()} is the folder it is stored under and appears in public URLs. */
public enum MediaPurpose {
    AVATAR("avatar"),
    COMMUNITY_COVER("community-cover"),
    GROUP_IMAGE("group-image"),
    EVENT_IMAGE("event-image"),
    POST_IMAGE("post-image");

    private final String pathSegment;

    MediaPurpose(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String pathSegment() {
        return pathSegment;
    }
}
