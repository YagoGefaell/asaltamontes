package io.github.yagogefaell.asaltamontes.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.FollowRequest;
import io.github.yagogefaell.asaltamontes.user.dto.FollowRequestDto;

@Repository
public interface FollowRequestRepository extends JpaRepository<FollowRequest, Long> {

    boolean existsByFollowerIdAndFollowedId(Long followerId, Long followedId);

    /** Among {@code ids}, the owners the follower has a pending request to (one query). */
    @Query("SELECT r.followed.id FROM FollowRequest r WHERE r.follower.id = :followerId AND r.followed.id IN :ids")
    List<Long> findRequestedAmong(@Param("followerId") Long followerId, @Param("ids") Collection<Long> ids);

    /** Inserts the request unless it exists; the count says whether this call created it (race-safe). */
    @Modifying
    @Query(value = """
            INSERT INTO follow_requests (follower_id, followed_id, created_at) VALUES (:followerId, :followedId, now())
            ON CONFLICT (follower_id, followed_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    /** Deletes it; only the call that really deleted the row (count 1) goes on to approve it. */
    @Modifying
    @Query("DELETE FROM FollowRequest r WHERE r.follower.id = :followerId AND r.followed.id = :followedId")
    int deleteRequest(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Query(value = """
            SELECT new io.github.yagogefaell.asaltamontes.user.dto.FollowRequestDto(
                u.username, p.fullName, p.profilePictureUrl, r.createdAt)
            FROM FollowRequest r
            JOIN r.follower u
            LEFT JOIN u.profile p
            WHERE r.followed.id = :ownerId
            ORDER BY r.createdAt DESC, r.id DESC
            """,
            countQuery = "SELECT count(r) FROM FollowRequest r WHERE r.followed.id = :ownerId")
    Page<FollowRequestDto> findInbox(@Param("ownerId") Long ownerId, Pageable pageable);

    /** Deletes every pending request to {@code ownerId} and returns who made them (her profile turned public). */
    @Query(value = "DELETE FROM follow_requests WHERE followed_id = :ownerId RETURNING follower_id", nativeQuery = true)
    List<Long> deleteAllTo(@Param("ownerId") Long ownerId);

    /** Removes both directions of pending requests between two users (a block). */
    @Modifying
    @Query("""
            DELETE FROM FollowRequest r
            WHERE (r.follower.id = :a AND r.followed.id = :b) OR (r.follower.id = :b AND r.followed.id = :a)
            """)
    int deleteBetween(@Param("a") Long a, @Param("b") Long b);
}
