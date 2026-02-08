package io.github.yagogefaell.asaltamontes.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.user.domain.UserProfile;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUserId(long userId);
    boolean existsByUserId(long userId);
    Optional<UserProfile> findByUser(io.github.yagogefaell.asaltamontes.user.domain.UserAccount user);
}