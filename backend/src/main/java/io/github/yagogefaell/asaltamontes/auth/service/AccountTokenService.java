package io.github.yagogefaell.asaltamontes.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.domain.AccountToken;
import io.github.yagogefaell.asaltamontes.auth.domain.AccountTokenPurpose;
import io.github.yagogefaell.asaltamontes.auth.repository.AccountTokenRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/**
 * One-time tokens for emails (T24a): 32 random bytes, only their SHA-256 is stored, a single use, and asking for a new one
 * replaces the previous for the same user and purpose. The raw token is returned once, to be put in the email, and is never logged.
 */
@Service
@RequiredArgsConstructor
public class AccountTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountTokenRepository repository;

    /** A token that was used: who it belongs to and the data it carried. */
    public record Consumed(Long userId, String payload) { }

    @Transactional
    public String issue(UserAccount user, AccountTokenPurpose purpose, Duration ttl) {
        return issue(user, purpose, ttl, null);
    }

    @Transactional
    public String issue(UserAccount user, AccountTokenPurpose purpose, Duration ttl, String payload) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        repository.deleteByUserAndPurpose(user.getId(), purpose);
        repository.save(new AccountToken(user, purpose, hash(raw), LocalDateTime.now().plus(ttl), payload));
        return raw;
    }

    /** Uses the token. Returns the id of its user, or empty if it is unknown, already used, replaced, expired or for another purpose. */
    @Transactional
    public Optional<Long> consume(String raw, AccountTokenPurpose purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 128) {
            return Optional.empty();
        }
        return repository.consume(hash(raw), purpose.name(), LocalDateTime.now());
    }

    /** Like {@link #consume} but also returns the payload of the token. */
    @Transactional
    public Optional<Consumed> consumeWithPayload(String raw, AccountTokenPurpose purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 128) {
            return Optional.empty();
        }
        return repository.consumeWithPayload(hash(raw), purpose.name(), LocalDateTime.now())
                .map(row -> new Consumed(row.getUserId(), row.getPayload()));
    }

    /** Drops the live token of the user for this purpose, if any. */
    @Transactional
    public void invalidate(Long userId, AccountTokenPurpose purpose) {
        repository.deleteByUserAndPurpose(userId, purpose);
    }

    @Transactional
    public int deleteExpired() {
        return repository.deleteExpired(LocalDateTime.now());
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
