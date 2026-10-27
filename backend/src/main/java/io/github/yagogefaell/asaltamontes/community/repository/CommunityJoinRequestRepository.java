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
        """;

    @Query(PENDING + " ORDER BY r.createdAt DESC, r.id DESC")
    List<JoinRequestDto> findFirstPage(@Param("communityId") Long communityId, Limit limit);

    @Query(PENDING + """
          AND (r.createdAt < :beforeAt OR (r.createdAt = :beforeAt AND r.id < :beforeId))
        ORDER BY r.createdAt DESC, r.id DESC
        """)
    List<JoinRequestDto> findPageBefore(@Param("communityId") Long communityId, @Param("beforeAt") Instant beforeAt,
                                        @Param("beforeId") Long beforeId, Limit limit);
}
