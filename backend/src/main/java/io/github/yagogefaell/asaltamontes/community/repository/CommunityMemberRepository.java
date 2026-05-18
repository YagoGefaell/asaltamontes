package io.github.yagogefaell.asaltamontes.community.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDto;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunityDto(
            c.id, 
            c.name, 
            c.description, 
            c.imageUrl, 
            creator.username 
        )
        FROM CommunityMember m 
        JOIN m.community c 
        LEFT JOIN c.createdBy creator
        WHERE m.userAccount.id = :userId
    """)
    Page<CommunityDto> findCommunitiesByUserId(@Param("userId") Long userId, Pageable pageable);
    @Modifying
    @Query("""
        DELETE FROM CommunityMember cm 
        WHERE cm.community.id = :communityId 
          AND cm.userAccount.id = :userId
    """)
    int removeMember(@Param("communityId") Long communityId, @Param("userId") Long userId);
}
