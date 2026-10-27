package io.github.yagogefaell.asaltamontes.community.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto(
            c.publicId,
            c.name,
            c.slug,
            c.imageUrl,
            m.role,
            c.memberCount,
            c.visibility,
            c.city,
            c.province
        )
        FROM CommunityMember m 
        JOIN m.community c
        WHERE m.userAccount.id = :userId
          AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
    """)
    Page<CommunitySearchDto> findCommunitiesByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT m.status FROM CommunityMember m
        WHERE m.community.id = :communityId AND m.userAccount.id = :userId
    """)
    Optional<MemberStatus> findStatus(@Param("communityId") Long communityId, @Param("userId") Long userId);

    /** Whether the user is the owner or a moderator of the community (and not banned). */
    @Query("""
        SELECT COUNT(m) > 0 FROM CommunityMember m
        WHERE m.community.id = :communityId AND m.userAccount.id = :userId
          AND m.role IN (io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.OWNER,
                         io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.MODERATOR)
          AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
    """)
    boolean isModerator(@Param("communityId") Long communityId, @Param("userId") Long userId);

    Optional<CommunityMember> findByCommunityIdAndUserAccountId(Long communityId, Long userId);

    /** The member's row with a write lock: moderation actions on the same member run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT m FROM CommunityMember m
        WHERE m.community.id = :communityId AND m.userAccount.id = :userId
    """)
    Optional<CommunityMember> lockMember(@Param("communityId") Long communityId, @Param("userId") Long userId);

    /** Whether the user is the owner (and not banned). */
    @Query("""
        SELECT COUNT(m) > 0 FROM CommunityMember m
        WHERE m.community.id = :communityId AND m.userAccount.id = :userId
          AND m.role = io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.OWNER
          AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
    """)
    boolean isOwner(@Param("communityId") Long communityId, @Param("userId") Long userId);

    /** User ids of the owner and the moderators: who gets the join requests. */
    @Query("""
        SELECT m.userAccount.id FROM CommunityMember m
        WHERE m.community.id = :communityId
          AND m.role IN (io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.OWNER,
                         io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.MODERATOR)
          AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
    """)
    List<Long> findStaffUserIds(@Param("communityId") Long communityId);

    @Modifying
    @Query("""
        DELETE FROM CommunityMember cm 
        WHERE cm.community.id = :communityId 
          AND cm.userAccount.id = :userId
          AND cm.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
    """)
    int removeMember(@Param("communityId") Long communityId, @Param("userId") Long userId);

    /** The silence follows user + community, not the membership: it survives leaving and joining again (T20c). */
    @Modifying
    @Query(value = "INSERT INTO community_mutes (community_id, user_id) VALUES (:communityId, :userId) ON CONFLICT DO NOTHING",
           nativeQuery = true)
    int addMute(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM community_mutes WHERE community_id = :communityId AND user_id = :userId", nativeQuery = true)
    int removeMute(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM community_mutes WHERE community_id = :communityId AND user_id = :userId)",
           nativeQuery = true)
    boolean hasMute(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Modifying
    @Query("""
        DELETE FROM CommunityMember cm 
        WHERE cm.community.id = :communityId
    """)
    void deleteAllByCommunityId(@Param("communityId") Long communityId);

    /** What decides whether she may write in the community chat: her status and whether the chat is on (T20). */
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.ChatAccess(m.status, c.chatEnabled)
        FROM CommunityMember m JOIN m.community c
        WHERE c.id = :communityId AND m.userAccount.id = :userId
    """)
    Optional<io.github.yagogefaell.asaltamontes.community.dto.ChatAccess> findChatAccess(
            @Param("communityId") Long communityId, @Param("userId") Long userId);
}
