package io.github.yagogefaell.asaltamontes.user.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.UserFollow;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;
import jakarta.transaction.Transactional;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /** Among {@code ids}, those who follow {@code userId} (one query). */
    @Query("SELECT f.follower.id FROM UserFollow f WHERE f.following.id = :userId AND f.follower.id IN :ids")
    List<Long> findFollowerIdsAmong(Long userId, Collection<Long> ids);

    /** Among {@code ids}, those {@code userId} follows (one query). */
    @Query("SELECT f.following.id FROM UserFollow f WHERE f.follower.id = :userId AND f.following.id IN :ids")
    List<Long> findFollowingIdsAmong(Long userId, Collection<Long> ids);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM UserFollow uf
        WHERE uf.follower.id = :followerId
        AND uf.following.id = :followingId
    """)
    int deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
            u.username,
            u.profile.fullName,
            u.profile.profilePictureUrl,
            CASE 
            WHEN u.id = :currentUserId THEN null
            WHEN EXISTS (
                SELECT 1 FROM UserFollow f2
                WHERE f2.follower.id = :currentUserId
                AND f2.following.id = u.id
            ) THEN true ELSE false END
        )
        FROM UserFollow f
        JOIN f.following u
        WHERE LOWER(f.follower.username) = LOWER(:username)
        AND NOT EXISTS (
            SELECT 1 FROM UserBlock bl
            WHERE (bl.blocker.id = :currentUserId AND bl.blocked.id = u.id) OR (bl.blocked.id = :currentUserId AND bl.blocker.id = u.id)
        )
        ORDER BY u.username ASC
    """)
    Page<UserSearchDto> findFollowingByUsername(String username, Long currentUserId, Pageable pageable);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
            u.username,
            u.profile.fullName,
            u.profile.profilePictureUrl,
            CASE 
            WHEN u.id = :currentUserId THEN null
            WHEN EXISTS (
                SELECT 1 FROM UserFollow f2
                WHERE f2.follower.id = :currentUserId
                AND f2.following.id = u.id
            ) THEN true ELSE false END
        )
        FROM UserFollow f
        JOIN f.follower u
        WHERE LOWER(f.following.username) = LOWER(:username)
        AND NOT EXISTS (
            SELECT 1 FROM UserBlock bl
            WHERE (bl.blocker.id = :currentUserId AND bl.blocked.id = u.id) OR (bl.blocked.id = :currentUserId AND bl.blocker.id = u.id)
        )
        ORDER BY u.username ASC
    """)
    Page<UserSearchDto> findFollowersByUsername(String username, Long currentUserId, Pageable pageable);
}