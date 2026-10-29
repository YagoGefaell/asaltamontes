package io.github.yagogefaell.asaltamontes.community.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityRepository extends JpaRepository<Community, Long> {

    // The API only knows public ids (D-021); served by the unique index on public_id
    Optional<Community> findByPublicId(UUID publicId);

    @Query("SELECT c.id FROM Community c WHERE c.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    @Query("SELECT COUNT(c) > 0 FROM Community c WHERE c.id = :id AND c.visibility = io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility.PUBLIC")
    boolean isPublic(@Param("id") Long id);

    @Query("SELECT c.id FROM Community c WHERE c.slug = :slug")
    Optional<Long> findIdBySlug(@Param("slug") String slug);

    /** Row lock for the actions that change the community itself (edit, transfer), so two cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Community c WHERE c.id = :id")
    Optional<Community> lockById(@Param("id") Long id);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto(
            u.username,
            u.profile.fullName,
            u.profile.profilePictureUrl,
            cm.role,
            cm.status,
            (CASE WHEN f.id IS NOT NULL THEN true ELSE false END)
        )
        FROM CommunityMember cm
        JOIN cm.userAccount u
        LEFT JOIN UserFollow f ON f.following = u AND f.follower.id = :currentUserId
        WHERE cm.community.id = :communityId
          AND u.id <> :currentUserId
          AND cm.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
          AND NOT EXISTS (
              SELECT 1 FROM UserBlock bl
              WHERE (bl.blocker.id = :currentUserId AND bl.blocked.id = u.id)
                 OR (bl.blocked.id = :currentUserId AND bl.blocker.id = u.id)
          )
    """)
    Page<CommunityMemberDto> findMembersExcept(@Param("communityId") Long communityId, @Param("currentUserId") Long currentUserId, Pageable pageable);

    // pattern: lowercase, with LIKE wildcards escaped and wrapped in % (CommunityService); city and province:
    // lowercase, or "" for "any" (never null: a null parameter has no type for PostgreSQL)
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
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :user AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
        WHERE LOWER(c.name) LIKE :pattern ESCAPE '\\'
          AND (:city = '' OR LOWER(c.city) = :city)
          AND (:province = '' OR LOWER(c.province) = :province)
    """)
    Page<CommunitySearchDto> searchCommunities(@Param("user") Long user, @Param("pattern") String pattern,
                                               @Param("city") String city, @Param("province") String province, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto(
            c.publicId,
            c.name,
            c.slug,
            c.description,
            c.imageUrl,
            m.role,
            c.memberCount,
            c.visibility,
            c.city,
            c.province,
            (CASE WHEN EXISTS (SELECT 1 FROM CommunityJoinRequest r WHERE r.communityId = c.id AND r.userId = :userId)
                  THEN true ELSE false END)
        )
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :userId AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
        WHERE c.slug = :slug
    """)
    Optional<CommunityDetailsDto> findCommunityBySlug(@Param("slug") String slug, @Param("userId") Long userId);

    // Links shared before T14 use the name
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto(
            c.publicId,
            c.name,
            c.slug,
            c.description,
            c.imageUrl,
            m.role,
            c.memberCount,
            c.visibility,
            c.city,
            c.province,
            (CASE WHEN EXISTS (SELECT 1 FROM CommunityJoinRequest r WHERE r.communityId = c.id AND r.userId = :userId)
                  THEN true ELSE false END)
        )
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :userId AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
        WHERE LOWER(c.name) = LOWER(:name)
    """)
    Optional<CommunityDetailsDto> findCommunityByName(@Param("name") String name, @Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Community c SET c.memberCount = c.memberCount + 1 WHERE c.id = :id")
    void incrementMemberCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Community c SET c.memberCount = GREATEST(c.memberCount - 1, 0) WHERE c.id = :id")
    void decrementMemberCount(@Param("id") Long id);

    @Query("SELECT c.id FROM Community c")
    Slice<Long> findAllIds(Pageable pageable);

    @Modifying
    @Query("""
        UPDATE Community c
        SET c.memberCount = (
            SELECT COUNT(m) FROM CommunityMember m
            WHERE m.community = c
              AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
        )
        WHERE c.id IN :ids
    """)
    void recalculateMemberCountByIds(@Param("ids") List<Long> ids);
}
