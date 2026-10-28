package io.github.yagogefaell.asaltamontes.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
            u.username,
            p.fullName,
            p.profilePictureUrl,
            (CASE WHEN f.id IS NOT NULL THEN true ELSE false END)
        )
        FROM UserAccount u
        LEFT JOIN u.profile p
        LEFT JOIN UserFollow f ON f.following = u AND f.follower.id = :userId
        WHERE u.id <> :userId
        AND NOT EXISTS (
            SELECT 1 FROM UserBlock bl
            WHERE (bl.blocker.id = :userId AND bl.blocked.id = u.id) OR (bl.blocked.id = :userId AND bl.blocker.id = u.id)
        )
        AND (:query IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    Page<UserSearchDto> searchUsersByUsername(
        @Param("query") String query, 
        @Param("userId") Long userId, 
        Pageable pageable
    );

    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
            u.username,
            p.fullName,
            p.profilePictureUrl,
            (CASE WHEN f.id IS NOT NULL THEN true ELSE false END)
        )
        FROM UserAccount u
        LEFT JOIN u.profile p
        LEFT JOIN UserFollow f ON f.following = u AND f.follower.id = :userId
        WHERE u.id <> :userId
        AND NOT EXISTS (
            SELECT 1 FROM UserBlock bl
            WHERE (bl.blocker.id = :userId AND bl.blocked.id = u.id) OR (bl.blocked.id = :userId AND bl.blocker.id = u.id)
        )
    """)
    Page<UserSearchDto> searchAllUsers(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * Users to follow (T31b): followed by someone {@code userId} follows, or in a community with her. Never herself,
     * anyone she already follows or has a block with in either direction. Most followed first.
     */
    @Query(value = """
        SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
            u.username,
            p.fullName,
            p.profilePictureUrl,
            false
        )
        FROM UserAccount u
        LEFT JOIN u.profile p
        WHERE u.id <> :userId
        AND NOT EXISTS (SELECT 1 FROM UserFollow own WHERE own.follower.id = :userId AND own.following.id = u.id)
        AND NOT EXISTS (
            SELECT 1 FROM UserBlock bl
            WHERE (bl.blocker.id = :userId AND bl.blocked.id = u.id) OR (bl.blocked.id = :userId AND bl.blocker.id = u.id)
        )
        AND (
            EXISTS (
                SELECT 1 FROM UserFollow mine, UserFollow theirs
                WHERE mine.follower.id = :userId AND theirs.follower.id = mine.following.id AND theirs.following.id = u.id
            )
            OR EXISTS (
                SELECT 1 FROM CommunityMember mm, CommunityMember om
                WHERE mm.userAccount.id = :userId AND om.community.id = mm.community.id AND om.userAccount.id = u.id
                AND mm.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
                AND om.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
            )
        )
        ORDER BY COALESCE(p.followersCount, 0) DESC, u.username ASC
    """, countQuery = """
        SELECT COUNT(u)
        FROM UserAccount u
        WHERE u.id <> :userId
        AND NOT EXISTS (SELECT 1 FROM UserFollow own WHERE own.follower.id = :userId AND own.following.id = u.id)
        AND NOT EXISTS (
            SELECT 1 FROM UserBlock bl
            WHERE (bl.blocker.id = :userId AND bl.blocked.id = u.id) OR (bl.blocked.id = :userId AND bl.blocker.id = u.id)
        )
        AND (
            EXISTS (
                SELECT 1 FROM UserFollow mine, UserFollow theirs
                WHERE mine.follower.id = :userId AND theirs.follower.id = mine.following.id AND theirs.following.id = u.id
            )
            OR EXISTS (
                SELECT 1 FROM CommunityMember mm, CommunityMember om
                WHERE mm.userAccount.id = :userId AND om.community.id = mm.community.id AND om.userAccount.id = u.id
                AND mm.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
                AND om.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED
            )
        )
    """)
    Page<UserSearchDto> findSuggestions(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END 
        FROM UserAccount u 
        WHERE LOWER(u.username) = LOWER(:username)
        AND u.id <> :userId
    """)
    boolean existsByUsernameExceptId(@Param("username") String username, @Param("userId") Long userId);

    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END
        FROM UserAccount u
        WHERE LOWER(u.email) = LOWER(:email)
        AND u.id <> :id
    """)
    boolean existsByEmailIgnoreCaseExceptId(@Param("email") String email, @Param("id") Long id);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    @Query("SELECT u FROM UserAccount u WHERE LOWER(u.username) IN :usernames")
    java.util.List<UserAccount> findByLowerUsernameIn(@Param("usernames") java.util.Collection<String> usernames);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
}