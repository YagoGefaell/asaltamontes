package io.github.yagogefaell.asaltamontes.notification.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.notification.domain.NotificationPreference;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationPreferenceId;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreferenceId> {

    List<NotificationPreference> findByUserId(Long userId);

    /** Among {@code userIds}, the ones that switched {@code type} off. */
    @Query("SELECT p.userId FROM NotificationPreference p WHERE p.type = :type AND p.enabled = false AND p.userId IN :userIds")
    List<Long> findUserIdsWithTypeDisabled(@Param("type") NotificationType type, @Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query(value = """
        INSERT INTO notification_preferences (user_id, type, enabled) VALUES (:userId, :type, :enabled)
        ON CONFLICT (user_id, type) DO UPDATE SET enabled = EXCLUDED.enabled
        """, nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("type") String type, @Param("enabled") boolean enabled);
}
