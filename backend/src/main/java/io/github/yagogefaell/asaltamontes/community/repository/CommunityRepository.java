package io.github.yagogefaell.asaltamontes.community.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;

import java.util.Optional;

public interface CommunityRepository extends JpaRepository<Community, Long> {
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
        ORDER BY u.username ASC
    """)
    Page<CommunityMemberDto> findMembersExcept(@Param("communityId") Long communityId, @Param("currentUserId") Long currentUserId, Pageable pageable);
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto(
            c.id,
            c.name,
            c.imageUrl,
            CASE WHEN m.id IS NOT NULL THEN true ELSE false END
        ) 
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :user
    """)
    Page<CommunitySearchDto> findAllCommunities(@Param("user") Long user, Pageable pageable);

    boolean existsByName(String name);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto(
            c.id,
            c.name,
            c.description,
            c.imageUrl,
            CASE WHEN c.createdBy.id = :userId THEN true ELSE false END,
            CASE WHEN m.id IS NOT NULL THEN true ELSE false END
        ) 
        FROM Community c
        LEFT JOIN CommunityMember m ON m.community = c AND m.userAccount.id = :userId
        WHERE c.name = :name
    """)
    Optional<CommunityDetailsDto> findCommunityByName(@Param("name") String name, @Param("userId") Long userId);
}
