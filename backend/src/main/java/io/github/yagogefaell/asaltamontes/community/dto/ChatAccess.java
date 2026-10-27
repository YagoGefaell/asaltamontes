package io.github.yagogefaell.asaltamontes.community.dto;

import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;

/** A member's standing for the community chat (T20): internal, never returned by the API. */
public record ChatAccess(MemberStatus status, boolean chatEnabled) {
}
