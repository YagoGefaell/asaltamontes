package io.github.yagogefaell.asaltamontes.community.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto(
            c.id,
            c.name,
            c.slug,
            c.imageUrl,
            m.role,
            c.memberCount
        )
        FROM CommunityMember m 
        JOIN m.community c
        WHERE m.userAccount.id = :userId
    """)
    Page<CommunitySearchDto> findCommunitiesByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT m.status FROM CommunityMember m
        WHERE m.community.id = :communityId AND m.userAccount.id = :userId
    """)
    Optional<MemberStatus> findStatus(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Modifying
    @Query("""
        DELETE FROM CommunityMember cm 
        WHERE cm.community.id = :communityId 
          AND cm.userAccount.id = :userId
    """)
    int removeMember(@Param("communityId") Long communityId, @Param("userId") Long userId);

    @Modifying
    @Query("""
        DELETE FROM CommunityMember cm 
        WHERE cm.community.id = :communityId
    """)
    void deleteAllByCommunityId(@Param("communityId") Long communityId);
}
