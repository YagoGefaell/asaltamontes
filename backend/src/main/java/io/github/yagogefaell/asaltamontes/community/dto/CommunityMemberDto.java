package io.github.yagogefaell.asaltamontes.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;

public record CommunityMemberDto(
    String username,
    String fullName,
    String profilePictureUrl,
    CommunityRole role,
    // Only the owner and the moderators see it (a muted member must not be marked for the rest); absent otherwise
    @JsonInclude(JsonInclude.Include.NON_NULL) MemberStatus status,
    Boolean isFollowing
) {
    public CommunityMemberDto withoutStatus() {
        return new CommunityMemberDto(username, fullName, profilePictureUrl, role, null, isFollowing);
    }
}
