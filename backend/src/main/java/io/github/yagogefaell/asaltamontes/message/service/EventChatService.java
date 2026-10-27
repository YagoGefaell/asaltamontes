package io.github.yagogefaell.asaltamontes.message.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.domain.EventStatus;
import io.github.yagogefaell.asaltamontes.event.dto.EventTiming;
import io.github.yagogefaell.asaltamontes.event.repository.EventAttendeeRepository;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.message.domain.ChatType;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ManagedBy;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.domain.MessageType;
import io.github.yagogefaell.asaltamontes.message.dto.EventChatDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/**
 * The chat of each event (T21): a group conversation managed by the event ({@code managed_by = EVENT}) whose
 * participants are the attendees that are GOING (the waitlist is out). Nobody adds or expels by hand: joining,
 * leaving and being promoted from the waitlist move people in and out. Everybody who enters reads the whole history
 * (decision of Yago, 2026-10-27). It becomes read only when the event is cancelled and 7 days after it ended
 * ("archived"); that is computed from the event, so it needs no job. Cancelling or moving the event leaves a system
 * message in the chat.
 *
 * <p>The chat is created with the event (and lazily for events that predate it). Every method runs in the
 * transaction of the attendance change, so chat and attendees never drift apart.
 */
@Service
@RequiredArgsConstructor
public class EventChatService {

    public static final Duration ARCHIVE_AFTER = Duration.ofDays(7);
    public static final String CANCELLED = "CANCELLED";
    public static final String ARCHIVED = "ARCHIVED";

    private static final String NOT_FOUND = "Chat no encontrado";

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final EventRepository eventRepository;
    private final EventAttendeeRepository attendeeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserActionLimiter limiter;

    /**
     * The chat of an event she is going to (created if missing). Everybody else, the waitlist included, gets the
     * same 404 as an event that does not exist, so the chat cannot be probed.
     */
    @Transactional
    public EventChatDto open(UUID eventPublicId, UserAccount user) {
        Long eventId = eventRepository.findIdByPublicId(eventPublicId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        if (attendeeRepository.findStatus(eventId, user.getId()).orElse(null) != AttendeeStatus.GOING) {
            throw new NotFoundException(NOT_FOUND);
        }
        // Also repairs a missing participation (e.g. an event that predates the chat)
        addAttendee(eventId, user.getId());
        UUID chatId = conversationRepository.findEventChatPublicId(eventId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        String reason = readOnlyReason(eventRepository.findTiming(eventId).orElseThrow(() -> new NotFoundException(NOT_FOUND)), Instant.now());
        return new EventChatDto(chatId, reason == null, reason);
    }

    /** Id of the event's chat; creates it with the people going if it does not exist yet. */
    @Transactional
    public Long ensureChat(Long eventId) {
        return conversationRepository.findEventChatId(eventId).orElseGet(() -> create(eventId));
    }

    private Long create(Long eventId) {
        // The event row is locked (the same lock join and leave take), so two requests creating the chat end up with one
        eventRepository.lockById(eventId);
        return conversationRepository.findEventChatId(eventId).orElseGet(() -> {
            Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
            Conversation chat = new Conversation();
            chat.setType(ChatType.GROUP);
            chat.setGroupName(event.getTitle());
            chat.setGroupImage(event.getImageUrl());
            chat.setManagedBy(ManagedBy.EVENT);
            chat.setManagedRefId(eventId);
            chat.setCreatedAt(now());
            Conversation saved = conversationRepository.saveAndFlush(chat);
            participantRepository.addEventAttendees(saved.getId(), eventId, now());
            return saved.getId();
        });
    }

    /** She is going (joined, or got a place from the waitlist): she is in the chat. Call after the attendee row is flushed. */
    @Transactional
    public Long addAttendee(Long eventId, Long userId) {
        Long chatId = ensureChat(eventId);
        participantRepository.addEventAttendeeIfAbsent(chatId, eventId, userId, now());
        return chatId;
    }

    /** She left the event: she is out of the chat and her sessions drop it from the list. */
    @Transactional
    public void removeAttendee(Long eventId, Long userId, String reason) {
        Long chatId = conversationRepository.findEventChatId(eventId).orElse(null);
        if (chatId != null && participantRepository.deleteByConversationIdAndUserId(chatId, userId) > 0) {
            conversationRepository.findEventChatPublicId(eventId)
                    .ifPresent(publicId -> eventPublisher.publishEvent(new ConversationRemovedEvent(userId, publicId, reason)));
        }
    }

    /** The event changed its title: the chat shows the same. */
    @Transactional
    public void syncProfile(Event event) {
        conversationRepository.updateEventChatProfile(event.getId(), event.getTitle(), event.getImageUrl());
    }

    /** The event was cancelled: everybody in the chat reads it; the chat is read only from now on. */
    @Transactional
    public void eventCancelled(Event event, Long actorId) {
        Map<String, Object> payload = payload("EVENT_CANCELLED");
        payload.put("reason", event.getCancelReason());
        postSystem(ensureChat(event.getId()), actorId, "La quedada se ha cancelado", payload);
    }

    /** The time or the place changed: {@code changed} is a subset of startsAt, timezone, city, meetingPoint. */
    @Transactional
    public void eventUpdated(Event event, Long actorId, List<String> changed) {
        Map<String, Object> payload = payload("EVENT_UPDATED");
        payload.put("changed", changed);
        payload.put("startsAt", event.getStartsAt().toString());
        payload.put("timeZone", event.getTimezone());
        postSystem(ensureChat(event.getId()), actorId, "La quedada ha cambiado", payload);
    }

    /**
     * Whether she may write in this conversation if an event manages it (nothing to check otherwise): the event must
     * not be cancelled or archived, and a per-user flood limit applies because every message reaches all attendees.
     */
    @Transactional(readOnly = true)
    public void requireCanWrite(Long conversationId, Long userId) {
        Long eventId = conversationRepository.findManagingEventId(conversationId).orElse(null);
        if (eventId == null) {
            return;
        }
        String reason = readOnlyReason(eventRepository.findTiming(eventId).orElseThrow(() -> new NotFoundException(NOT_FOUND)), Instant.now());
        if (CANCELLED.equals(reason)) {
            throw new ConflictException("La quedada está cancelada: el chat es de solo lectura");
        }
        if (ARCHIVED.equals(reason)) {
            throw new ConflictException("El chat de esta quedada está archivado y es de solo lectura");
        }
        limiter.eventMessage(userId);
    }

    /** Null while the chat is open; CANCELLED or ARCHIVED (7 days after the event ended) when it is read only. */
    public static String readOnlyReason(EventTiming event, Instant now) {
        if (event.status() == EventStatus.CANCELLED) {
            return CANCELLED;
        }
        Instant end = event.endsAt() != null ? event.endsAt() : event.startsAt().plus(EventService.DEFAULT_DURATION);
        return now.isAfter(end.plus(ARCHIVE_AFTER)) ? ARCHIVED : null;
    }

    private static Map<String, Object> payload(String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        return payload;
    }

    /**
     * Saves a system message, makes it the chat's last message, counts it as unread for everybody but the actor and
     * publishes it (the broadcaster sends it to the participants once the transaction commits).
     */
    private void postSystem(Long chatId, Long actorId, String content, Map<String, Object> payload) {
        LocalDateTime at = now();
        Conversation chat = conversationRepository.getReferenceById(chatId);
        Message message = new Message();
        message.setConversation(chat);
        message.setType(MessageType.SYSTEM);
        message.setContent(content);
        message.setSystemPayload(payload);
        message.setSentAt(at);
        Message saved = messageRepository.save(message);

        conversationRepository.updateLastMessage(chatId, MessageService.preview(content), at, null);
        participantRepository.incrementUnreadExceptSender(chatId, actorId);
        eventPublisher.publishEvent(new MessageSentEvent(new MessageDto(saved.getPublicId(), chat.getPublicId(), null, content, at,
                MessageType.SYSTEM, payload)));
    }

    private static LocalDateTime now() {
        return OpaqueCursor.truncate(LocalDateTime.now());
    }
}
