package io.github.yagogefaell.asaltamontes.event.dto;

/** A person going to an event, as shown in the stacked avatars of a card (T31). Users are exposed by username (D-021). */
public record EventAttendeePreviewDto(String username, String fullName, Avatar avatar) {

    /** Small variant of the picture; the whole {@code avatar} is null when she has none. */
    public record Avatar(String thumb) {}
}
