package io.github.yagogefaell.asaltamontes.community.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything waiting for the caller around communities (T31c). Each list is capped (see the service), newest first.
 * There are no "received invitations": communities have no invitation feature yet.
 */
public record PendingCommunitiesDto(List<ToApprove> toApprove, List<Mine> mine) {

    public record Community(UUID id, String name, String slug, String imageUrl) {
    }

    /** Join requests waiting for the caller, who is owner or moderator; requests from blocked users are not counted. */
    public record ToApprove(Community community, long pendingCount, Instant latestRequestAt) {
    }

    /** A request of her own to join a private community, still undecided. */
    public record Mine(UUID id, Community community, Instant requestedAt) {
    }
}
