package io.github.yagogefaell.asaltamontes.user.profiles;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUserId(long userId);
    boolean existsByUserId(long userId);
    Optional<UserProfile> findByUser(io.github.yagogefaell.asaltamontes.user.account.UserAccount user);
}