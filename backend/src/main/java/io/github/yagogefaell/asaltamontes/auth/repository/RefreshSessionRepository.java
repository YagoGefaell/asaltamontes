package io.github.yagogefaell.asaltamontes.auth.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.auth.domain.RefreshSession;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

    @Modifying
    @Query("""
        UPDATE RefreshSession s
        SET s.revokedAt = :now
        WHERE s.jti = :jti AND s.revokedAt IS NULL
    """)
    int revoke(@Param("jti") String jti, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        UPDATE RefreshSession s
        SET s.revokedAt = :now
        WHERE s.user.id = :userId AND s.revokedAt IS NULL
    """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshSession s WHERE s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
