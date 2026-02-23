package io.github.yagogefaell.asaltamontes.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.UserFollow;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchFollowDto;
import jakarta.transaction.Transactional;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM UserFollow uf
        WHERE uf.follower.id = :followerId
        AND uf.following.id = :followingId
    """)
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchFollowDto(
            u.username,
            u.profile.fullName,
            u.profile.profilePictureUrl,
            CASE WHEN EXISTS (
                SELECT 1 FROM UserFollow f2
                WHERE f2.follower.id = :followerId
                AND f2.following.id = u.id
            ) THEN true ELSE false END
        )
        FROM UserFollow f
        JOIN f.following u
        WHERE f.follower.id = :followerId
        ORDER BY u.username ASC
    """)
    Page<UserSearchFollowDto> findFollowingByFollowerId(Long followerId, Pageable pageable);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchFollowDto(
            u.username,
            u.profile.fullName,
            u.profile.profilePictureUrl,
            CASE WHEN EXISTS (
                SELECT 1 FROM UserFollow f2
                WHERE f2.follower.id = :followingId
                AND f2.following.id = u.id
            ) THEN true ELSE false END
        )
        FROM UserFollow f
        JOIN f.follower u
        WHERE f.following.id = :followingId
        ORDER BY u.username ASC
    """)
    Page<UserSearchFollowDto> findFollowersByFollowingId(Long followingId, Pageable pageable);
}