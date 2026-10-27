package io.github.yagogefaell.asaltamontes.notification.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/**
 * A notification for one user (T18). Rows are written in batches with JDBC by {@code NotificationService} (an
 * event can notify hundreds of people); this mapping is for reading and for marking as read.
 */
@Entity
@Getter
@Table(name = "notifications")
public class Notification extends PublicIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private Long recipientId;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", updatable = false, length = 20)
    private NotificationTargetType targetType;

    @Column(name = "target_ref", updatable = false)
    private String targetRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
