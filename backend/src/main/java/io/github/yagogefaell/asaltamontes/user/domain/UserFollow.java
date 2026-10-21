package io.github.yagogefaell.asaltamontes.user.domain;

import java.time.LocalDateTime;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Index;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "user_follow",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"follower_id", "following_id"}
    ),
    indexes = {
        @Index(name = "idx_follower", columnList = "follower_id"),
        @Index(name = "idx_following", columnList = "following_id")
    }
)
public class UserFollow {

    public UserFollow(UserAccount follower, UserAccount following) {
        this.follower = follower;
        this.following = following;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id")
    private UserAccount follower;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id")
    private UserAccount following;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Identity by primary key (Hibernate's recommended pattern): no lazy relations in equals/hashCode/toString,
    // stable across persist and valid for Hibernate proxies.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserFollow other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return UserFollow.class.hashCode();
    }

    @Override
    public String toString() {
        return "UserFollow{id=" + id + "}";
    }
}
