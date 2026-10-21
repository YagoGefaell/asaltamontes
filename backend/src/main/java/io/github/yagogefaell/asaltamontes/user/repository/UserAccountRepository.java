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
    """)
    Page<UserSearchDto> searchAllUsers(@Param("userId") Long userId, Pageable pageable);
    
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
    Optional<UserAccount> findByEmailIgnoreCase(String email);
}