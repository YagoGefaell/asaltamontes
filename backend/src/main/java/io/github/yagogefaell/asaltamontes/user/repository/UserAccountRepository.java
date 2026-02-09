package io.github.yagogefaell.asaltamontes.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount,Long> {
    @Query("SELECT u FROM UserAccount u " +
       "WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) " +
       "AND u.id <> :id")
    List<UserAccount> searchUsersByUsername(@Param("query") String query,  @Param("id") Long id);
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserAccount u WHERE u.username = :username AND u.id <> :userId")
    boolean existsByUsernameExceptId(@Param("username") String username, @Param("userId") Long userId);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsername(String username);
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    @Query("""
        SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END
        FROM UserAccount u
        WHERE LOWER(u.email) = LOWER(:email)
        AND u.id <> :id
    """)
    boolean existsByEmailIgnoreCaseExceptId(String email, Long idd);
}
