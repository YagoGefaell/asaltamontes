package io.github.yagogefaell.asaltamontes.community.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityModerationLog;
import io.github.yagogefaell.asaltamontes.community.dto.BannedMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.ModerationLogEntryDto;

public interface CommunityModerationLogRepository extends JpaRepository<CommunityModerationLog, Long> {

    /** Only entries of that community: the cursor of another community's entry is simply not found. */
    Optional<CommunityModerationLog> findByPublicIdAndCommunityId(UUID publicId, Long communityId);

    /** When her latest request to join was rejected, if ever (cooldown before she can ask again). */
    @Query("""
        SELECT MAX(l.createdAt) FROM CommunityModerationLog l
        WHERE l.communityId = :communityId AND l.targetId = :userId
          AND l.action = io.github.yagogefaell.asaltamontes.community.domain.ModerationAction.REQUEST_REJECTED
        """)
    Optional<Instant> findLastRejection(@Param("communityId") Long communityId, @Param("userId") Long userId);

    String LOG = """
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.ModerationLogEntryDto(
            l.publicId, l.action, a.username, t.username, l.reason, l.createdAt)
        FROM CommunityModerationLog l
        LEFT JOIN UserAccount a ON a.id = l.actorId
        LEFT JOIN UserAccount t ON t.id = l.targetId
        WHERE l.communityId = :communityId
        """;

    @Query(LOG + " ORDER BY l.createdAt DESC, l.id DESC")
    List<ModerationLogEntryDto> findFirstPage(@Param("communityId") Long communityId, Limit limit);

    @Query(LOG + """
          AND (l.createdAt < :beforeAt OR (l.createdAt = :beforeAt AND l.id < :beforeId))
        ORDER BY l.createdAt DESC, l.id DESC
        """)
    List<ModerationLogEntryDto> findPageBefore(@Param("communityId") Long communityId, @Param("beforeAt") Instant beforeAt,
                                               @Param("beforeId") Long beforeId, Limit limit);

    /** Currently banned members with the data of the latest BAN entry (when, why, by whom). */
    String BANNED = """
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.BannedMemberDto(
            l.publicId, t.username, t.profile.fullName, t.profile.profilePictureUrl, l.createdAt, l.reason, a.username)
        FROM CommunityModerationLog l
        JOIN UserAccount t ON t.id = l.targetId
        LEFT JOIN UserAccount a ON a.id = l.actorId
        WHERE l.communityId = :communityId
          AND l.action = io.github.yagogefaell.asaltamontes.community.domain.ModerationAction.BAN
          AND l.id = (SELECT MAX(l2.id) FROM CommunityModerationLog l2
                      WHERE l2.communityId = :communityId AND l2.targetId = l.targetId
                        AND l2.action = io.github.yagogefaell.asaltamontes.community.domain.ModerationAction.BAN)
          AND EXISTS (SELECT 1 FROM CommunityMember m
                      WHERE m.community.id = :communityId AND m.userAccount.id = l.targetId
                        AND m.status = io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED)
        """;

    @Query(BANNED + " ORDER BY l.createdAt DESC, l.id DESC")
    List<BannedMemberDto> findFirstBannedPage(@Param("communityId") Long communityId, Limit limit);

    @Query(BANNED + """
          AND (l.createdAt < :beforeAt OR (l.createdAt = :beforeAt AND l.id < :beforeId))
        ORDER BY l.createdAt DESC, l.id DESC
        """)
    List<BannedMemberDto> findBannedPageBefore(@Param("communityId") Long communityId, @Param("beforeAt") Instant beforeAt,
                                               @Param("beforeId") Long beforeId, Limit limit);
}
