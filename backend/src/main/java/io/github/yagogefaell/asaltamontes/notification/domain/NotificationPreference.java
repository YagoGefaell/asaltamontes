package io.github.yagogefaell.asaltamontes.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

/** One switch of a user's settings. A missing row means the type is enabled. Written with an upsert. */
@Entity
@Getter
@Table(name = "notification_preferences")
@IdClass(NotificationPreferenceId.class)
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private NotificationType type;

    @Column(nullable = false)
    private boolean enabled;
}
