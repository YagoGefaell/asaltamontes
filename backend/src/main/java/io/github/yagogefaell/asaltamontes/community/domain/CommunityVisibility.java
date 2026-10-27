package io.github.yagogefaell.asaltamontes.community.domain;

/** PRIVATE communities need the approval of the moderation to join; outsiders only see the public card. */
public enum CommunityVisibility {
    PUBLIC,
    PRIVATE
}
