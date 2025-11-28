package io.github.yagogefaell.asaltamontes.profiles;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    java.util.Optional<UserProfile> findByUserId(long userId);
}