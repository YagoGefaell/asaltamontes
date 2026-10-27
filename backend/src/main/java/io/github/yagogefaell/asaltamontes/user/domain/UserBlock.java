package io.github.yagogefaell.asaltamontes.user.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code blocker} does not want any interaction with {@code blocked}; every check treats it as symmetric. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "user_blocks")
@IdClass(UserBlock.Key.class)
public class UserBlock {

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long blocker;
        private Long blocked;
    }

    public UserBlock(UserAccount blocker, UserAccount blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = LocalDateTime.now();
    }

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id")
    private UserAccount blocker;

    @Id
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id")
    private UserAccount blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
