package io.github.yagogefaell.asaltamontes.notification.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.github.yagogefaell.asaltamontes.community.service.JoinRequestDecided;
import io.github.yagogefaell.asaltamontes.community.service.JoinRequested;
import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.service.AttendeePromoted;
import io.github.yagogefaell.asaltamontes.event.service.EventAttendeeJoined;
import io.github.yagogefaell.asaltamontes.event.service.EventCancelled;
import io.github.yagogefaell.asaltamontes.event.service.EventNotificationSource;
import io.github.yagogefaell.asaltamontes.event.service.EventReminder;
import io.github.yagogefaell.asaltamontes.event.service.EventNotificationSource.EventInfo;
import io.github.yagogefaell.asaltamontes.event.service.EventUpdated;
import io.github.yagogefaell.asaltamontes.infrastructure.config.AsyncConfig;
import io.github.yagogefaell.asaltamontes.message.service.AddedToGroup;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationTargetType;
import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationService.Delivery;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationService.NewNotification;
import io.github.yagogefaell.asaltamontes.post.service.PostCommented;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.service.UserFollowed;

/**
 * Turns domain events into notifications (T18). It runs after the commit of the action that published the event, on
 * its own thread pool: a failure here is logged and never reaches the action (following still answers 204).
 * Adding a notification type means one more method here (and its value in {@link NotificationType}).
 *
 * <p>The actor never notifies herself and blocks (T13) are honoured: both are enforced in
 * {@link NotificationService#create}.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private static final Set<AttendeeStatus> ACTIVE = Set.of(AttendeeStatus.GOING, AttendeeStatus.WAITLIST);

    private final NotificationService notificationService;
    private final EventNotificationSource eventSource;
    private final UserAccountRepository userAccountRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationListener(NotificationService notificationService, EventNotificationSource eventSource,
                                UserAccountRepository userAccountRepository, SimpMessagingTemplate messagingTemplate) {
        this.notificationService = notificationService;
        this.eventSource = eventSource;
        this.userAccountRepository = userAccountRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserFollowed(UserFollowed event) {
        safely("NEW_FOLLOWER", () -> {
            String username = userAccountRepository.findById(event.followerId()).map(u -> u.getUsername()).orElse(null);
            if (username == null) {
                return;
            }
            deliver(new NewNotification(NotificationType.NEW_FOLLOWER, event.followerId(), NotificationTargetType.USER,
                    username, Map.of()), List.of(event.followedId()));
        });
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventAttendeeJoined(EventAttendeeJoined event) {
        safely("EVENT_JOINED", () -> eventSource.find(event.eventId()).ifPresent(info -> {
            Map<String, Object> payload = eventPayload(info);
            payload.put("waitlisted", event.waitlisted());
            deliver(new NewNotification(NotificationType.EVENT_JOINED, event.userId(), NotificationTargetType.EVENT,
                    info.publicId().toString(), payload), List.of(info.organizerId()));
        }));
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventUpdated(EventUpdated event) {
        safely("EVENT_UPDATED", () -> eventSource.find(event.eventId()).ifPresent(info -> {
            Map<String, Object> payload = eventPayload(info);
            payload.put("changed", event.changedFields());
            deliver(new NewNotification(NotificationType.EVENT_UPDATED, event.actorId(), NotificationTargetType.EVENT,
                    info.publicId().toString(), payload), eventSource.attendeeUserIds(event.eventId(), ACTIVE));
        }));
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventCancelled(EventCancelled event) {
        safely("EVENT_CANCELLED", () -> eventSource.find(event.eventId()).ifPresent(info -> {
            Map<String, Object> payload = eventPayload(info);
            payload.put("reason", event.reason());
            deliver(new NewNotification(NotificationType.EVENT_CANCELLED, event.actorId(), NotificationTargetType.EVENT,
                    info.publicId().toString(), payload), eventSource.attendeeUserIds(event.eventId(), ACTIVE));
        }));
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttendeePromoted(AttendeePromoted event) {
        safely("WAITLIST_PROMOTED", () -> eventSource.find(event.eventId()).ifPresent(info ->
                deliver(new NewNotification(NotificationType.WAITLIST_PROMOTED, null, NotificationTargetType.EVENT,
                        info.publicId().toString(), eventPayload(info)), List.of(event.userId()))));
    }

    /** 24 h or 2 h before the start (T21): to everybody who is going. No actor, so no block applies. */
    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEventReminder(EventReminder event) {
        safely("EVENT_REMINDER", () -> eventSource.find(event.eventId()).ifPresent(info -> {
            Map<String, Object> payload = eventPayload(info);
            payload.put("hoursBefore", event.hoursBefore());
            deliver(new NewNotification(NotificationType.EVENT_REMINDER, null, NotificationTargetType.EVENT,
                    info.publicId().toString(), payload), eventSource.attendeeUserIds(event.eventId(), Set.of(AttendeeStatus.GOING)));
        }));
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAddedToGroup(AddedToGroup event) {
        safely("ADDED_TO_GROUP", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("groupName", event.groupName());
            deliver(new NewNotification(NotificationType.ADDED_TO_GROUP, event.actorId(), NotificationTargetType.CONVERSATION,
                    event.conversationId().toString(), payload), event.recipientIds());
        });
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequested(JoinRequested event) {
        safely("COMMUNITY_JOIN_REQUEST", () -> deliver(new NewNotification(NotificationType.COMMUNITY_JOIN_REQUEST,
                event.requesterId(), NotificationTargetType.COMMUNITY, event.communitySlug(),
                communityPayload(event.communityName())), event.staffIds()));
    }

    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequestDecided(JoinRequestDecided event) {
        NotificationType type = event.approved() ? NotificationType.COMMUNITY_JOIN_APPROVED : NotificationType.COMMUNITY_JOIN_REJECTED;
        // A rejection does not say which moderator decided
        Long actor = event.approved() ? event.actorId() : null;
        safely(type.name(), () -> deliver(new NewNotification(type, actor, NotificationTargetType.COMMUNITY,
                event.communitySlug(), communityPayload(event.communityName())), List.of(event.requesterId())));
    }

    /** To the author of the post; the actor herself and blocks are filtered in {@link NotificationService#create}. */
    @Async(AsyncConfig.NOTIFICATIONS_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCommented(PostCommented event) {
        safely("POST_COMMENT", () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("excerpt", event.excerpt());
            deliver(new NewNotification(NotificationType.POST_COMMENT, event.commenterId(), NotificationTargetType.POST,
                    event.postPublicId().toString(), payload), List.of(event.postAuthorId()));
        });
    }

    private static Map<String, Object> communityPayload(String communityName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("communityName", communityName);
        return payload;
    }

    /** Title, start as a UTC instant and the event's own IANA zone: the client formats the time (D-019). */
    static Map<String, Object> eventPayload(EventInfo info) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", info.title());
        payload.put("startsAt", info.startsAt().toString());
        payload.put("timeZone", info.timezone());
        return payload;
    }

    private void deliver(NewNotification notification, Collection<Long> recipients) {
        for (Delivery delivery : notificationService.create(notification, recipients)) {
            messagingTemplate.convertAndSendToUser(String.valueOf(delivery.recipientId()), "/queue/notifications",
                    delivery.notification());
        }
    }

    /** A notification that cannot be made is logged, not thrown: it must never undo or break what caused it. */
    private void safely(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.error("Could not create {} notifications", what, e);
        }
    }
}
