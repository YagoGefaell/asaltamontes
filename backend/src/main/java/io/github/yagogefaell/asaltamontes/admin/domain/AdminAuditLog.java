package io.github.yagogefaell.asaltamontes.admin.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.common.identifier.PublicIdEntity;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;
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

/** One administrative action, written in the same transaction as the action itself. Internal: no endpoint exposes it yet. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "admin_audit_log")
public class AdminAuditLog extends PublicIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AdminAction action;

    @Column(name = "report_id", updatable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", updatable = false)
    private ReportTargetType targetType;

    @Column(name = "target_public_id", updatable = false)
    private UUID targetPublicId;

    @Column(name = "target_user_id", updatable = false)
    private Long targetUserId;

    @Column(length = 1000, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
