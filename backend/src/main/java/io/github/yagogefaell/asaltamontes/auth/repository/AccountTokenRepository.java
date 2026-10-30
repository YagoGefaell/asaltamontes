package io.github.yagogefaell.asaltamontes.auth.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.auth.domain.AccountToken;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    /** Replaces the previous token of the user for this purpose (one live token per user and purpose). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AccountToken t WHERE t.user.id = :userId AND t.purpose = :purpose")
    void deleteByUserAndPurpose(@Param("userId") Long userId, @Param("purpose") io.github.yagogefaell.asaltamontes.auth.domain.AccountTokenPurpose purpose);

    /**
     * Uses a token: deletes it and returns its user in one atomic statement, so two concurrent requests with the same
     * token cannot both succeed. Empty if it does not exist, is for another purpose or has expired.
     */
    @Query(value = """
        DELETE FROM account_tokens
        WHERE token_hash = :hash AND purpose = :purpose AND expires_at > :now
        RETURNING user_id
        """, nativeQuery = true)
    Optional<Long> consume(@Param("hash") String hash, @Param("purpose") String purpose, @Param("now") LocalDateTime now);

    /** Same as {@link #consume} but also returns the payload of the token. */
    @Query(value = """
        DELETE FROM account_tokens
        WHERE token_hash = :hash AND purpose = :purpose AND expires_at > :now
        RETURNING user_id AS userId, payload AS payload
        """, nativeQuery = true)
    Optional<ConsumedToken> consumeWithPayload(@Param("hash") String hash, @Param("purpose") String purpose, @Param("now") LocalDateTime now);

    interface ConsumedToken {
        Long getUserId();

        String getPayload();
    }

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
