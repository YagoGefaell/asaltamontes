package io.github.yagogefaell.asaltamontes.notification.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.notification.domain.Notification;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    String ROW = """
        SELECT new io.github.yagogefaell.asaltamontes.notification.repository.NotificationRow(
            n.publicId, n.recipientId, n.type, a.username, p.fullName, p.profilePictureUrl,
            n.targetType, n.targetRef, n.payload, n.readAt, n.createdAt)
        FROM Notification n
        LEFT JOIN UserAccount a ON a.id = n.actorId
        LEFT JOIN a.profile p
        """;

    /** T18: hidden at read time (nothing is deleted) when the recipient blocked the actor; unblocking brings them back. */
    String NOT_FROM_BLOCKED = """
          AND NOT EXISTS (SELECT 1 FROM UserBlock b WHERE b.blocker.id = n.recipientId AND b.blocked.id = n.actorId)
        """;

    @Query(ROW + " WHERE n.recipientId = :recipientId " + NOT_FROM_BLOCKED + " ORDER BY n.createdAt DESC, n.id DESC")
    List<NotificationRow> findFirstPage(@Param("recipientId") Long recipientId, Limit limit);

    @Query(ROW + """
        WHERE n.recipientId = :recipientId
          AND (n.createdAt < :beforeAt OR (n.createdAt = :beforeAt AND n.id < :beforeId))
        """ + NOT_FROM_BLOCKED + """
        ORDER BY n.createdAt DESC, n.id DESC
        """)
    List<NotificationRow> findPageBefore(@Param("recipientId") Long recipientId, @Param("beforeAt") Instant beforeAt,
                                         @Param("beforeId") Long beforeId, Limit limit);

    @Query(ROW + " WHERE n.publicId IN :publicIds")
    List<NotificationRow> findRowsByPublicIds(@Param("publicIds") Collection<UUID> publicIds);

    /** Only her own notifications: the cursor of someone else's, or a missing one, is simply not found. */
    Optional<Notification> findByPublicIdAndRecipientId(UUID publicId, Long recipientId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientId = :recipientId AND n.readAt IS NULL " + NOT_FROM_BLOCKED)
    long countUnread(@Param("recipientId") Long recipientId);

    long countByRecipientIdAndPublicIdIn(Long recipientId, Collection<UUID> publicIds);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientId = :recipientId AND n.publicId IN :publicIds AND n.readAt IS NULL")
    int markRead(@Param("recipientId") Long recipientId, @Param("publicIds") Collection<UUID> publicIds, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);

    /** Among {@code recipientIds}, those who already got a notification of this type from the actor since {@code since}. */
    @Query("SELECT DISTINCT n.recipientId FROM Notification n WHERE n.actorId = :actorId AND n.type = :type AND n.recipientId IN :recipientIds AND n.createdAt > :since")
    List<Long> findRecipientsNotifiedBy(@Param("actorId") Long actorId, @Param("type") NotificationType type,
                                        @Param("recipientIds") Collection<Long> recipientIds, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.type = :type AND n.targetRef = :targetRef AND n.readAt IS NULL AND n.recipientId IN :recipientIds")
    int deleteUnread(@Param("type") NotificationType type, @Param("targetRef") String targetRef, @Param("recipientIds") Collection<Long> recipientIds);

    /** Deletes up to {@code batch} notifications older than the cutoff, read or not as asked; returns how many. */
    @Modifying
    @Query(value = """
        DELETE FROM notifications WHERE id IN (
            SELECT id FROM notifications
            WHERE created_at < :cutoff AND ((read_at IS NOT NULL) = :read)
            LIMIT :batch)
        """, nativeQuery = true)
    int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("read") boolean read, @Param("batch") int batch);
}
