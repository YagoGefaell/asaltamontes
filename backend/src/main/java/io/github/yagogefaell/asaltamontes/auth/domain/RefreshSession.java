package io.github.yagogefaell.asaltamontes.auth.domain;

import java.time.LocalDateTime;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A refresh token that has been issued, identified by its jti claim. The token itself is never stored.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @Column(length = 36)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public RefreshSession(String jti, UserAccount user, LocalDateTime expiresAt) {
        this.jti = jti;
        this.user = user;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}
