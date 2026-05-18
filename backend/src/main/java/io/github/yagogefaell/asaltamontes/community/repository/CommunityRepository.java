package io.github.yagogefaell.asaltamontes.community.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;

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
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunityDto(
            c.id, 
            c.name, 
            c.description, 
            c.imageUrl, 
            c.createdBy.username
        ) 
        FROM Community c
    """)
    Page<CommunityDto> findAllCommunities(Pageable pageable);
    boolean existsByName(String name);
    Optional<CommunityDto> findCommunityById(Long id);
}
