package io.github.yagogefaell.asaltamontes.community.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityJoinRequest;
import io.github.yagogefaell.asaltamontes.community.dto.JoinRequestDto;
import io.github.yagogefaell.asaltamontes.community.dto.PendingCommunitiesDto;

public interface CommunityJoinRequestRepository extends JpaRepository<CommunityJoinRequest, Long> {

    /** Native and conflict-tolerant: two identical requests at once leave one row and the second returns 0. */
    @Modifying
    @Query(value = """
        INSERT INTO community_join_requests (public_id, community_id, user_id, created_at)
        VALUES (:publicId, :communityId, :userId, :createdAt)
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("publicId") UUID publicId, @Param("communityId") Long communityId,
                       @Param("userId") Long userId, @Param("createdAt") Instant createdAt);

    /** Deleting is how a request is decided or withdrawn; the count says whether this call is the one that did. */
    @Modifying
    @Query("DELETE FROM CommunityJoinRequest r WHERE r.communityId = :communityId AND r.userId = :userId")
    int deleteRequest(@Param("communityId") Long communityId, @Param("userId") Long userId);

    Optional<CommunityJoinRequest> findByPublicIdAndCommunityId(UUID publicId, Long communityId);

    String PENDING = """
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.JoinRequestDto(
            r.publicId, u.username, u.profile.fullName, u.profile.profilePictureUrl, r.createdAt)
        FROM CommunityJoinRequest r
        JOIN UserAccount u ON u.id = r.userId
        WHERE r.communityId = :communityId
          AND NOT EXISTS (
              SELECT 1 FROM UserBlock bl
              WHERE (bl.blocker.id = :viewerId AND bl.blocked.id = r.userId)
                 OR (bl.blocked.id = :viewerId AND bl.blocker.id = r.userId))
        """;

    /** Requests from users with a block against the viewer in either direction are left out (D-022, BUG-079). */
    @Query(PENDING + " ORDER BY r.createdAt DESC, r.id DESC")
    List<JoinRequestDto> findFirstPage(@Param("communityId") Long communityId, @Param("viewerId") Long viewerId,
                                       Limit limit);

    @Query(PENDING + """
          AND (r.createdAt < :beforeAt OR (r.createdAt = :beforeAt AND r.id < :beforeId))
        ORDER BY r.createdAt DESC, r.id DESC
        """)
    List<JoinRequestDto> findPageBefore(@Param("communityId") Long communityId, @Param("viewerId") Long viewerId,
                                        @Param("beforeAt") Instant beforeAt, @Param("beforeId") Long beforeId, Limit limit);

    /**
     * Communities where {@code userId} is owner or moderator (not banned) with undecided requests, newest activity
     * first (T31c). Requests from users with a block against her in either direction are not counted (D-022).
     */
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.PendingCommunitiesDto$ToApprove(
            new io.github.yagogefaell.asaltamontes.community.dto.PendingCommunitiesDto$Community(
                c.publicId, c.name, c.slug, c.imageUrl),
            COUNT(r), MAX(r.createdAt))
        FROM CommunityJoinRequest r
        JOIN Community c ON c.id = r.communityId
        JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :userId
        WHERE m.role IN (io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.OWNER,
                         io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.MODERATOR)
          AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
          AND NOT EXISTS (
              SELECT 1 FROM UserBlock bl
              WHERE (bl.blocker.id = :userId AND bl.blocked.id = r.userId)
                 OR (bl.blocked.id = :userId AND bl.blocker.id = r.userId))
        GROUP BY c.id, c.publicId, c.name, c.slug, c.imageUrl
        ORDER BY MAX(r.createdAt) DESC, c.id
        """)
    List<PendingCommunitiesDto.ToApprove> findToApprove(@Param("userId") Long userId, Limit limit);

    /** Her own undecided requests, newest first (T31c). */
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.PendingCommunitiesDto$Mine(
            r.publicId,
            new io.github.yagogefaell.asaltamontes.community.dto.PendingCommunitiesDto$Community(
                c.publicId, c.name, c.slug, c.imageUrl),
            r.createdAt)
        FROM CommunityJoinRequest r
        JOIN Community c ON c.id = r.communityId
        WHERE r.userId = :userId
        ORDER BY r.createdAt DESC, r.id DESC
        """)
    List<PendingCommunitiesDto.Mine> findMine(@Param("userId") Long userId, Limit limit);
}
