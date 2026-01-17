package io.github.yagogefaell.asaltamontes.user.account;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount,Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserAccount u WHERE u.username = :username AND u.id <> :userId")
    boolean existsByUsernameExceptId(@Param("username") String username, @Param("userId") Long userId);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsername(String username);
    Page<UserAccount> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}
