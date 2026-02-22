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
public interface UserAccountRepository extends JpaRepository<UserAccount,Long> {
    @Query("""
    SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
        u.username,
        p.fullName,
        p.profilePictureUrl
    )
    FROM UserAccount u
    LEFT JOIN u.profile p
    WHERE u.username LIKE %:query%
    AND u.id <> :excludeId
    """)
    Page<UserSearchDto> searchUsersByUsername(String query, Long excludeId, Pageable pageable);

    @Query("""
    SELECT new io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto(
        u.username,
        p.fullName,
        p.profilePictureUrl
    )
    FROM UserAccount u
    LEFT JOIN u.profile p
    WHERE u.id <> :excludeId
    """)
    Page<UserSearchDto> searchAllUsers(Long excludeId, Pageable pageable);

    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END 
        FROM UserAccount u 
        WHERE u.username = :username 
        AND u.id <> :userId
    """)
    boolean existsByUsernameExceptId(@Param("username") String username, @Param("userId") Long userId);

    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END
        FROM UserAccount u
        WHERE LOWER(u.email) = LOWER(:email)
        AND u.id <> :id
    """)
    boolean existsByEmailIgnoreCaseExceptId(String email, Long id);

    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsername(String username);
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
}
