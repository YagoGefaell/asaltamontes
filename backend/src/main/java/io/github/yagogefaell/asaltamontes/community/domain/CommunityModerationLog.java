package io.github.yagogefaell.asaltamontes.community.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import io.github.yagogefaell.asaltamontes.common.identifier.PublicIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One entry of the moderation log of a community (T19): who did what to whom, and when. Append-only. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "community_moderation_log")
public class CommunityModerationLog extends PublicIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private ModerationAction action;

    @Column(updatable = false, length = 300)
    private String reason;

    // Microseconds, like the column, so that a keyset taken from it compares exactly
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    public CommunityModerationLog(Long communityId, Long actorId, Long targetId, ModerationAction action, String reason) {
        this.communityId = communityId;
        this.actorId = actorId;
        this.targetId = targetId;
        this.action = action;
        this.reason = reason;
    }
}
