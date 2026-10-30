package io.github.yagogefaell.asaltamontes.report.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.common.identifier.PublicIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A report (T23b). Created once and then only handled by the moderation (T23c): who reported and who handled stay
 * internal. {@code targetId} is the internal key of the target; {@code targetPublicId} its public id (null for users).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "reports")
public class Report extends PublicIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "target_public_id", updatable = false)
    private UUID targetPublicId;

    @Column(name = "reported_user_id", updatable = false)
    private Long reportedUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReportReason reason;

    @Column(updatable = false, length = 1000)
    private String details;

    @Column(name = "content_snapshot", updatable = false, columnDefinition = "TEXT")
    private String contentSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(length = 1000)
    private String resolution;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
