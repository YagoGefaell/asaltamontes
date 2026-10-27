package io.github.yagogefaell.asaltamontes.notification.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.identifier.UuidV7;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.notification.domain.Notification;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationPreference;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationTargetType;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;
import io.github.yagogefaell.asaltamontes.notification.dto.ActorDto;
import io.github.yagogefaell.asaltamontes.notification.dto.MarkReadRequest;
import io.github.yagogefaell.asaltamontes.notification.dto.NotificationDto;
import io.github.yagogefaell.asaltamontes.notification.dto.NotificationPageDto;
import io.github.yagogefaell.asaltamontes.notification.dto.NotificationPreferencesDto;
import io.github.yagogefaell.asaltamontes.notification.dto.TargetDto;
import io.github.yagogefaell.asaltamontes.notification.dto.UpdatePreferencesRequest;
import io.github.yagogefaell.asaltamontes.notification.repository.NotificationPreferenceRepository;
import io.github.yagogefaell.asaltamontes.notification.repository.NotificationRepository;
import io.github.yagogefaell.asaltamontes.notification.repository.NotificationRow;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import lombok.RequiredArgsConstructor;

/**
 * Creates, lists and marks notifications (T18). Creation is for the listener (after the originating transaction
 * committed); the rest backs {@code /api/notifications}.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final int DEFAULT_PAGE = 20;
    public static final int MAX_PAGE = 50;
    public static final int MAX_READ_IDS = 50;

    static final String NOT_FOUND = "Notificación no encontrada";
    private static final int INSERT_BATCH = 500;
    private static final java.time.Duration FOLLOW_COOLDOWN = java.time.Duration.ofHours(24);

    /** One notification to send to many recipients. */
    public record NewNotification(NotificationType type, Long actorId, NotificationTargetType targetType,
                                  String targetRef, Map<String, Object> payload) {
    }

    /** A created notification and who gets it over WebSocket (sessions are identified by user id). */
    public record Delivery(Long recipientId, NotificationDto notification) {
    }

    private record Slot(UUID publicId, Long recipientId) {
    }

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final BlockService blockService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Inserts one row per recipient in batches, in its own transaction (after the commit of the action there is
     * none left). Leaves out: the actor herself, anyone with a block either way with the actor, anyone who switched
     * the type off, and what the dedupe indexes reject (a second unread NEW_FOLLOWER from the same person).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Delivery> create(NewNotification notification, Collection<Long> recipientIds) {
        Set<Long> recipients = new LinkedHashSet<>(recipientIds);
        if (notification.actorId() != null) {
            recipients.remove(notification.actorId());
            // T13: a block works both ways; the other side is never told, so nothing is created
            recipients.removeAll(blockService.blockedEitherWayAmong(notification.actorId(), recipients));
        }
        if (!recipients.isEmpty()) {
            recipients.removeAll(preferenceRepository.findUserIdsWithTypeDisabled(notification.type(), recipients));
        }
        if (notification.type() == NotificationType.NEW_FOLLOWER && notification.actorId() != null && !recipients.isEmpty()) {
            // Follow, read, unfollow, follow again must not become a way to ping someone over and over
            recipients.removeAll(notificationRepository.findRecipientsNotifiedBy(notification.actorId(),
                    NotificationType.NEW_FOLLOWER, recipients, Instant.now().minus(FOLLOW_COOLDOWN)));
        }
        if (recipients.isEmpty()) {
            return List.of();
        }
        if (notification.type() == NotificationType.EVENT_UPDATED) {
            // One unread "this event changed" per person and event: the newest replaces the previous
            notificationRepository.deleteUnread(notification.type(), notification.targetRef(), recipients);
        }

        String payload = json(notification.payload());
        Timestamp now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
        List<Slot> slots = new ArrayList<>(recipients.size());
        recipients.forEach(r -> slots.add(new Slot(UuidV7.next(), r)));

        // ON CONFLICT DO NOTHING: the unique partial indexes are the dedupe; the update count says what was inserted
        int[][] counts = jdbcTemplate.batchUpdate("""
                INSERT INTO notifications (public_id, recipient_id, actor_id, type, target_type, target_ref, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT DO NOTHING
                """, slots, INSERT_BATCH, (statement, slot) -> {
                    statement.setObject(1, slot.publicId());
                    statement.setLong(2, slot.recipientId());
                    statement.setObject(3, notification.actorId());
                    statement.setString(4, notification.type().name());
                    statement.setString(5, notification.targetType() == null ? null : notification.targetType().name());
                    statement.setString(6, notification.targetRef());
                    statement.setString(7, payload);
                    statement.setTimestamp(8, now);
                });

        List<UUID> inserted = new ArrayList<>();
        int index = 0;
        for (int[] chunk : counts) {
            for (int count : chunk) {
                if (count > 0 || count == java.sql.Statement.SUCCESS_NO_INFO) {
                    inserted.add(slots.get(index).publicId());
                }
                index++;
            }
        }
        if (inserted.isEmpty()) {
            return List.of();
        }
        return notificationRepository.findRowsByPublicIds(inserted).stream()
                .map(row -> new Delivery(row.recipientId(), toDto(row)))
                .toList();
    }

    /** Newest first. {@code before} is an opaque cursor naming one of her own notifications (400 otherwise). */
    @Transactional(readOnly = true)
    public NotificationPageDto list(Long userId, String before, int limit) {
        int pageSize = Math.clamp(limit, 1, MAX_PAGE);
        OpaqueCursor cursor = OpaqueCursor.decode(before, OpaqueCursor.NOTIFICATION);
        Limit fetch = Limit.of(pageSize + 1);
        List<NotificationRow> rows;
        if (cursor == null) {
            rows = notificationRepository.findFirstPage(userId, fetch);
        } else {
            // The position comes from her own row, not from the token, which only names it
            Notification at = notificationRepository.findByPublicIdAndRecipientId(cursor.publicId(), userId)
                    .orElseThrow(() -> new BadRequestException("Cursor no válido"));
            rows = notificationRepository.findPageBefore(userId, at.getCreatedAt(), at.getId(), fetch);
        }
        boolean hasMore = rows.size() > pageSize;
        List<NotificationRow> page = rows.subList(0, Math.min(pageSize, rows.size()));
        NotificationRow last = hasMore ? page.get(page.size() - 1) : null;
        return new NotificationPageDto(
                page.stream().map(NotificationService::toDto).toList(),
                hasMore,
                last == null ? null : OpaqueCursor.encode(OpaqueCursor.NOTIFICATION,
                        LocalDateTime.ofInstant(last.createdAt(), ZoneOffset.UTC), last.publicId()));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    /**
     * Marks the given notifications, or all of hers, as read. Idempotent. If any id is missing or someone else's
     * the answer is the same 404 and nothing is marked.
     */
    @Transactional
    public void markRead(Long userId, MarkReadRequest request) {
        boolean all = Boolean.TRUE.equals(request.all());
        boolean hasIds = request.ids() != null && !request.ids().isEmpty();
        if (all == hasIds) {
            throw new BadRequestException("Indica ids o all: true");
        }
        Instant now = Instant.now();
        if (all) {
            notificationRepository.markAllRead(userId, now);
            return;
        }
        if (request.ids().size() > MAX_READ_IDS) {
            throw new BadRequestException("Máximo " + MAX_READ_IDS + " notificaciones por petición");
        }
        if (request.ids().contains(null)) {
            throw new BadRequestException("Identificador no válido");
        }
        Set<UUID> ids = new LinkedHashSet<>(request.ids());
        if (notificationRepository.countByRecipientIdAndPublicIdIn(userId, ids) != ids.size()) {
            throw new NotFoundException(NOT_FOUND);
        }
        notificationRepository.markRead(userId, ids, now);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDto preferences(Long userId) {
        Map<String, Boolean> types = new LinkedHashMap<>();
        for (NotificationType type : NotificationType.values()) {
            types.put(type.name(), true);
        }
        for (NotificationPreference preference : preferenceRepository.findByUserId(userId)) {
            types.put(preference.getType().name(), preference.isEnabled());
        }
        return new NotificationPreferencesDto(types);
    }

    /** Only the types present change (D-020); a null value or an unknown type is a 400 and nothing changes. */
    @Transactional
    public NotificationPreferencesDto updatePreferences(Long userId, UpdatePreferencesRequest request) {
        if (request.types() == null) {
            throw new BadRequestException("Indica los tipos a cambiar");
        }
        Map<NotificationType, Boolean> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : request.types().entrySet()) {
            NotificationType type = parseType(entry.getKey());
            if (entry.getValue() == null) {
                throw new BadRequestException("El valor de " + entry.getKey() + " tiene que ser verdadero o falso");
            }
            changes.put(type, entry.getValue());
        }
        changes.forEach((type, enabled) -> preferenceRepository.upsert(userId, type.name(), enabled));
        return preferences(userId);
    }

    private static NotificationType parseType(String name) {
        try {
            return NotificationType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Tipo de notificación desconocido");
        }
    }

    static NotificationDto toDto(NotificationRow row) {
        ActorDto actor = row.actorUsername() == null ? null
                : new ActorDto(row.actorUsername(), row.actorFullName(), row.actorPicture());
        TargetDto target = row.targetType() == null ? null : new TargetDto(row.targetType(), row.targetRef());
        return new NotificationDto(row.publicId(), row.type(), actor, target,
                row.payload() == null ? Map.of() : row.payload(), row.readAt(), row.createdAt());
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload de notificación no serializable", e);
        }
    }
}
