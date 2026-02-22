package io.github.yagogefaell.asaltamontes.user.repository;

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

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM UserFollow uf
        WHERE uf.follower.id = :followerId
        AND uf.following.id = :followingId
    """)
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
            u.username,
            u.profile.fullName,
            u.profile.profilePictureUrl
        )
        FROM UserFollow f
        JOIN f.following u
        WHERE f.follower.id = :followerId
        ORDER BY u.username ASC
    """)
    Page<UserSearchDto> findFollowingByFollowerId(Long followerId, Pageable pageable);

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
            u.username,
            u.profile.fullName,
            u.profile.profilePictureUrl
        )
        FROM UserFollow f
        JOIN f.follower u
        WHERE f.following.id = :followingId
        ORDER BY u.username ASC
    """)
    Page<UserSearchDto> findFollowersByFollowingId(Long followingId, Pageable pageable);
}