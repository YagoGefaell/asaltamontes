package io.github.yagogefaell.asaltamontes.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A pending request to follow a private profile (T23a). Approving it turns it into a {@link UserFollow}. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "follow_requests")
public class FollowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id")
    private UserAccount follower;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "followed_id")
    private UserAccount followed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FollowRequest other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return FollowRequest.class.hashCode();
    }

    @Override
    public String toString() {
        return "FollowRequest{id=" + id + "}";
    }
}
