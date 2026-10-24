package io.github.yagogefaell.asaltamontes.community.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto(
            u.username, 
            u.profile.fullName, 
            u.profile.profilePictureUrl, 
            cm.role,
            (CASE WHEN f.id IS NOT NULL THEN true ELSE false END)
        )
        FROM CommunityMember cm 
        JOIN cm.userAccount u
        LEFT JOIN UserFollow f ON f.following = u AND f.follower.id = :currentUserId
        WHERE cm.community.id = :communityId 
          AND u.id <> :currentUserId
    """)
    Page<CommunityMemberDto> findMembersExcept(@Param("communityId") Long communityId, @Param("currentUserId") Long currentUserId, Pageable pageable);
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto(
            c.publicId,
            c.name,
            c.slug,
            c.imageUrl,
            m.role,
            c.memberCount
        ) 
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :user
    """)
    Page<CommunitySearchDto> findAllCommunities(@Param("user") Long user, Pageable pageable);

    // pattern: lowercase, with LIKE wildcards escaped and wrapped in % (CommunityService#search)
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto(
            c.publicId,
            c.name,
            c.slug,
            c.imageUrl,
            m.role,
            c.memberCount
        )
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :user
        WHERE LOWER(c.name) LIKE :pattern ESCAPE '\\'
    """)
    Page<CommunitySearchDto> searchCommunities(@Param("user") Long user, @Param("pattern") String pattern, Pageable pageable);

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
            c.memberCount
        ) 
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :userId
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
            c.memberCount
        ) 
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :userId
        WHERE LOWER(c.name) = LOWER(:name)
    """)
    Optional<CommunityDetailsDto> findCommunityByName(@Param("name") String name, @Param("userId") Long userId);

    boolean existsByIdAndCreatedById(Long communityId, Long userId);

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
        )
        WHERE c.id IN :ids
    """)
    void recalculateMemberCountByIds(@Param("ids") List<Long> ids);
}
