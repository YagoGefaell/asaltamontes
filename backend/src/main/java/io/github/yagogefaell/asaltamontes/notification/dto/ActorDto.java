package io.github.yagogefaell.asaltamontes.notification.dto;

/** {@code followedByMe}: whether the recipient of the notification already follows the actor. */
public record ActorDto(String username, String fullName, String profilePictureUrl, boolean followedByMe) {
}
