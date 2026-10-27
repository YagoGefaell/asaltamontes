package io.github.yagogefaell.asaltamontes.message.service;

import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.exceptions.UserNotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.message.domain.ChatType;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.domain.ManagedBy;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.domain.MessageType;
import io.github.yagogefaell.asaltamontes.message.domain.ParticipantRole;
import io.github.yagogefaell.asaltamontes.message.dto.ChatPageDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatResponseDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessagePageDto;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final ApplicationEventPublisher eventPublisher;
    private final MediaReferenceService mediaReferences;
    private final UserActionLimiter limiter;
    private final CommunityChatService communityChat;
    private final EventChatService eventChat;
    private final int maxGroupSize;

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int MAX_HISTORY_PAGE = 50;
    public static final int MAX_CHAT_PAGE = 50;
    private static final int PREVIEW_LENGTH = 30;
    private static final LocalDateTime FULL_HISTORY = LocalDateTime.of(1970, 1, 1, 0, 0);

    public MessageService(MessageRepository messageRepository, 
                          ConversationRepository conversationRepository,
                          ConversationParticipantRepository participantRepository,
                          UserService userService,
                          BlockService blockService,
                          ApplicationEventPublisher eventPublisher,
                          MediaReferenceService mediaReferences,
                          UserActionLimiter limiter,
                          CommunityChatService communityChat,
                          EventChatService eventChat,
                          @Value("${asaltamontes.chat.max-group-size:100}") int maxGroupSize) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.userService = userService;
        this.blockService = blockService;
        this.eventPublisher = eventPublisher;
        this.mediaReferences = mediaReferences;
        this.limiter = limiter;
        this.communityChat = communityChat;
        this.eventChat = eventChat;
        this.maxGroupSize = maxGroupSize;
    }

    /**
     * Saves a message from a participant and broadcasts it after commit (ChatMessageBroadcaster).
     * Counters and the conversation preview are updated with single statements, so concurrent
     * messages never lose an update.
     */
    @Transactional
    public MessageDto processIncomingMessage(UUID conversationPublicId, Long senderId, String content) {
        String text = validateContent(content);

        // Unknown and foreign chats look the same (404), so nobody can probe which chats exist
        Long conversationId = conversationPublicId == null ? null
                : conversationRepository.findIdByPublicId(conversationPublicId).orElse(null);
        if (conversationId == null || !participantRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw new NotFoundException("Chat no encontrado");
        }
        // Community chats: muted members only read, the moderation can switch the chat off (T20)
        communityChat.requireCanWrite(conversationId, senderId);
        // Event chats: read only once the event is cancelled or archived (T21)
        eventChat.requireCanWrite(conversationId, senderId);
        // A block closes the private chat in both directions; groups keep working (T13)
        participantRepository.findOtherUserIdInPrivateChat(conversationId, senderId)
                .ifPresent(otherId -> blockService.requireNotBlocked(senderId, otherId));

        UserAccount sender = userService.loadUserById(senderId);

        Message message = new Message();
        message.setConversation(conversationRepository.getReferenceById(conversationId));
        message.setSender(sender);
        message.setContent(text);
        // Microsecond precision, as stored: the message cursor carries sent_at and PostgreSQL rounds nanoseconds
        message.setSentAt(OpaqueCursor.truncate(LocalDateTime.now()));
        Message savedMessage = messageRepository.save(message);

        conversationRepository.updateLastMessage(conversationId, preview(text), savedMessage.getSentAt(), sender);
        participantRepository.incrementUnreadExceptSender(conversationId, senderId);

        MessageDto dto = new MessageDto(
                savedMessage.getPublicId(),
                conversationPublicId,
                sender.getUsername(),
                savedMessage.getContent(),
                savedMessage.getSentAt()
        );

        eventPublisher.publishEvent(new MessageSentEvent(dto));
        return dto;
    }

    /**
     * A moderator of a community deletes a message of its chat (T20c). Other chats answer 403; a chat the caller is not
     * in, or a message that is not in it, answer 404. The list preview is recomputed and the participants are told.
     */
    @Transactional
    public void deleteMessage(UUID conversationPublicId, UUID messagePublicId, Long userId) {
        Long conversationId = participantConversationId(conversationPublicId, userId);
        Long communityId = communityChat.requireModerator(conversationId, userId);
        Message message = messageRepository.findByPublicIdAndConversationId(messagePublicId, conversationId)
                .orElseThrow(() -> new NotFoundException("Mensaje no encontrado"));
        if (message.getType() != MessageType.USER) {
            throw new ConflictException("Solo se pueden borrar mensajes de usuarias");
        }
        Long authorId = message.getSender() == null ? null : message.getSender().getId();
        communityChat.requireCanDeleteFrom(communityId, userId, authorId);
        // One statement: two moderators deleting the same message at once get 204 and 404, never a 500
        if (messageRepository.deleteByIdReturningCount(message.getId()) == 0) {
            throw new NotFoundException("Mensaje no encontrado");
        }
        Message last = messageRepository.findFirstByConversationIdOrderByIdDesc(conversationId).orElse(null);
        if (last == null) {
            conversationRepository.replaceLastMessage(conversationId, null, null, null);
        } else if (last.getType() == MessageType.USER) {
            conversationRepository.replaceLastMessage(conversationId, preview(last.getContent()), last.getSentAt(), last.getSender());
        } else {
            conversationRepository.replaceLastMessage(conversationId, last.getContent(), last.getSentAt(), null);
        }
        communityChat.logMessageDeleted(communityId, userId, authorId);
        eventPublisher.publishEvent(new MessageDeletedEvent(conversationPublicId, messagePublicId));
    }

    /** The text shown in the chat list for the last message. */
    static String preview(String text) {
        return text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH).trim() + "..." : text;
    }

    private static String validateContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BadRequestException("El mensaje no puede estar vacío");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("El mensaje no puede superar los " + MAX_CONTENT_LENGTH + " caracteres");
        }
        return text;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ChatResponseDto> getPrivateConversation(String currentUsername, String recipientUsername) {
        return conversationRepository.findPrivateConversation(currentUsername, recipientUsername)
            .map(conv -> new ChatResponseDto(conv.getPublicId()));
    }

    @Transactional
    public ChatResponseDto createOrGetPrivateConversation(String currentUsername, String recipientUsername) {
        if (currentUsername.equalsIgnoreCase(recipientUsername)) {
            throw new BadRequestException("No puedes abrir un chat contigo misma");
        }

        UserAccount me = (UserAccount) userService.loadUserByUsername(currentUsername);
        UserAccount other = (UserAccount) userService.loadUserByUsername(recipientUsername);

        // Whoever blocked her looks like an unknown user, as in her profile
        if (blockService.hasBlocked(other.getId(), me.getId())) {
            throw new UserNotFoundException(recipientUsername);
        }

        Optional<ChatResponseDto> existingChat = getPrivateConversation(currentUsername, recipientUsername);

        if (existingChat.isPresent()) {
            // An existing chat survives dm_policy but not a block
            blockService.requireNotBlocked(me.getId(), other.getId());
            return existingChat.get();
        }

        blockService.requireCanStartPrivateChat(me.getId(), other.getId());

        Conversation newConv = createPrivateConversation(me, other);
        return new ChatResponseDto(newConv.getPublicId());
    }

    @Transactional
    public ChatResponseDto createGroupConversation(String currentUsername, String groupName, String groupImage,
                                                   UUID groupMediaId, List<String> users) {
        UserAccount creator = (UserAccount) userService.loadUserByUsername(currentUsername);
        limiter.groupCreate(creator.getId());
        // The creator administers the new group, so she may set its image; the image must be hers
        MediaReferenceService.Linked image = mediaReferences.link(groupMediaId, groupImage, MediaPurpose.GROUP_IMAGE,
                creator.getId(), null);

        if (users == null || users.isEmpty()) {
            throw new BadRequestException("Debes añadir al menos un usuario al grupo");
        }

        Set<String> normalizedUsernames = new LinkedHashSet<>();
        for (String username : users) {
            if (username == null || username.isBlank()) {
                continue;
            }
            if (!username.equalsIgnoreCase(currentUsername)) {
                normalizedUsernames.add(username.trim());
            }
        }

        if (normalizedUsernames.isEmpty()) {
            throw new BadRequestException("Debes añadir usuarios válidos distintos de tu cuenta");
        }

        // The creator is also a participant
        if (normalizedUsernames.size() + 1 > maxGroupSize) {
            throw new BadRequestException("Un grupo puede tener como máximo " + maxGroupSize + " participantes");
        }

        List<UserAccount> members = normalizedUsernames.stream()
                .map(username -> (UserAccount) userService.loadUserByUsername(username))
                .toList();

        List<Long> memberIds = members.stream().map(UserAccount::getId).toList();
        // Whoever blocked the creator looks like a user that does not exist (nobody can probe who blocked her)
        Set<Long> blockers = blockService.blockersAmong(creator.getId(), memberIds);
        for (UserAccount member : members) {
            if (blockers.contains(member.getId())) {
                throw new NotFoundException("Usuaria no encontrada: " + member.getUsername());
            }
        }
        // T13: no block either way and each one's dm_policy, with one neutral 403
        blockService.requireCanAddToGroup(creator.getId(), memberIds);

        Conversation newConv = createGroupConversation(creator, members, groupName, image);
        // T18: the members are told they were added (the creator is not notified of her own action)
        eventPublisher.publishEvent(new AddedToGroup(newConv.getPublicId(), newConv.getGroupName(), creator.getId(),
                creator.getUsername(), memberIds));
        return new ChatResponseDto(newConv.getPublicId());
    }

    /**
     * A page of the history (T10): the {@code limit} messages before {@code before} (or the latest ones),
     * oldest first. Reading no longer marks the chat as read: that is chat.read or POST /read.
     */
    @Transactional(readOnly = true)
    public MessagePageDto getConversationHistory(UUID conversationPublicId, Long userId, String beforeToken, int limit) {
        Long conversationId = participantConversationId(conversationPublicId, userId);
        Long before = messageCursor(beforeToken, conversationId);
        LocalDateTime since = historyStart(conversationId, userId);

        int pageSize = Math.clamp(limit, 1, MAX_HISTORY_PAGE);
        // One extra row tells whether there is a previous page
        Limit fetch = Limit.of(pageSize + 1);
        List<MessageDto> newestFirst = before == null
                ? messageRepository.findLatest(conversationId, since, fetch)
                : messageRepository.findBefore(conversationId, since, before, fetch);

        boolean hasMore = newestFirst.size() > pageSize;
        List<MessageDto> page = new ArrayList<>(newestFirst.subList(0, Math.min(pageSize, newestFirst.size())));
        Collections.reverse(page);
        String nextBefore = hasMore ? page.get(0).cursor() : null;

        return new MessagePageDto(page, hasMore, nextBefore);
    }

    /**
     * The {@code limit} messages after {@code after}, oldest first (T11: what arrived while the WebSocket was down).
     * {@code hasMore} means there are newer ones: ask again with after = cursor of the last item.
     */
    @Transactional(readOnly = true)
    public MessagePageDto getMessagesAfter(UUID conversationPublicId, Long userId, String afterToken, int limit) {
        Long conversationId = participantConversationId(conversationPublicId, userId);
        Long after = messageCursor(afterToken, conversationId);
        if (after == null) {
            throw new BadRequestException("Cursor no válido");
        }

        int pageSize = Math.clamp(limit, 1, MAX_HISTORY_PAGE);
        List<MessageDto> oldestFirst = messageRepository.findAfter(conversationId, historyStart(conversationId, userId),
                after, Limit.of(pageSize + 1));

        boolean hasMore = oldestFirst.size() > pageSize;
        List<MessageDto> page = List.copyOf(oldestFirst.subList(0, Math.min(pageSize, oldestFirst.size())));
        return new MessagePageDto(page, hasMore, null);
    }

    @Transactional
    public void markConversationAsRead(UUID conversationPublicId, Long userId) {
        Long conversationId = conversationPublicId == null ? null
                : conversationRepository.findIdByPublicId(conversationPublicId).orElse(null);
        if (conversationId == null || participantRepository.resetUnread(conversationId, userId) == 0) {
            throw new NotFoundException("Chat no encontrado");
        }
    }

    /** Internal id of a chat the user takes part in; unknown and foreign chats are the same 404 (no probing). */
    private Long participantConversationId(UUID conversationPublicId, Long userId) {
        Long conversationId = conversationRepository.findIdByPublicId(conversationPublicId).orElse(null);
        if (conversationId == null || !participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new NotFoundException("Chat no encontrado");
        }
        return conversationId;
    }

    /**
     * The first moment of the history this user may read (T12). In a manual group, someone who joined later sees
     * only what was sent since she joined, unless an admin made the history visible; chats managed by a community
     * or an event, and private chats, show everything.
     */
    private LocalDateTime historyStart(Long conversationId, Long userId) {
        ConversationParticipant participant = participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Chat no encontrado"));
        Conversation conversation = participant.getConversation();
        boolean fromJoin = conversation.getType() == ChatType.GROUP && conversation.getManagedBy() == ManagedBy.NONE
                && !conversation.isHistoryVisibleToNew();
        return fromJoin ? participant.getJoinedAt() : FULL_HISTORY;
    }

    /**
     * Internal id of the message a cursor points to (null when there is no cursor). The token must be well formed
     * and name a message of this very chat with that exact sent_at, so it cannot point into other chats: 400.
     */
    private Long messageCursor(String token, Long conversationId) {
        OpaqueCursor cursor = OpaqueCursor.decode(token, OpaqueCursor.MESSAGE);
        if (cursor == null) {
            return null;
        }
        if (cursor.order() == null) {
            throw new BadRequestException("Cursor no válido");
        }
        return messageRepository.findIdByPublicIdAndSentAt(cursor.publicId(), conversationId, cursor.order())
                .orElseThrow(() -> new BadRequestException("Cursor no válido"));
    }

    private Conversation createPrivateConversation(UserAccount user1, UserAccount user2) {
        Conversation conv = new Conversation();
        conv.setType(ChatType.INDIVIDUAL);
        conv.setCreatedAt(LocalDateTime.now());
        Conversation savedConv = conversationRepository.save(conv);

        ConversationParticipant p1 = new ConversationParticipant();
        p1.setConversation(savedConv);
        p1.setUser(user1);

        ConversationParticipant p2 = new ConversationParticipant();
        p2.setConversation(savedConv);
        p2.setUser(user2);

        participantRepository.saveAll(List.of(p1, p2));

        return savedConv;
    }

    private Conversation createGroupConversation(UserAccount creator, List<UserAccount> members, String groupName,
                                                 MediaReferenceService.Linked image) {
        Conversation conv = new Conversation();
        conv.setType(ChatType.GROUP);
        conv.setGroupName((groupName == null || groupName.isBlank()) ? "Nuevo grupo" : groupName.trim());
        conv.setGroupImage(image.url());
        conv.setGroupMediaId(image.mediaId());
        conv.setCreatedAt(LocalDateTime.now());
        conv.setCreatedBy(creator);
        Conversation savedConv = conversationRepository.save(conv);

        Set<Long> addedUsers = new LinkedHashSet<>();
        addedUsers.add(creator.getId());

        ConversationParticipant creatorParticipant = new ConversationParticipant();
        creatorParticipant.setConversation(savedConv);
        creatorParticipant.setUser(creator);
        creatorParticipant.setRole(ParticipantRole.ADMIN);

        List<ConversationParticipant> participants = new java.util.ArrayList<>();
        participants.add(creatorParticipant);

        for (UserAccount member : members) {
            if (member == null || addedUsers.contains(member.getId())) {
                continue;
            }

            ConversationParticipant participant = new ConversationParticipant();
            participant.setConversation(savedConv);
            participant.setUser(member);
            participants.add(participant);
            addedUsers.add(member.getId());
        }

        participantRepository.saveAll(participants);
        return savedConv;
    }

    /** A page of the chat list (T10b): the {@code limit} chats after the cursor, most recent first. */
    @Transactional(readOnly = true)
    public ChatPageDto getUserConversations(String username, String beforeToken, int limit) {
        OpaqueCursor cursor = OpaqueCursor.decode(beforeToken, OpaqueCursor.CHAT);
        // The cursor chat has to be one of hers. Its order value is only a keyset position in her own list
        // (it may differ from the chat's current last_message_at if a message arrived meanwhile), so it is not
        // checked against the row: the queries below only ever return her chats.
        Long beforeId = cursor == null ? null
                : conversationRepository.findIdByPublicIdAndParticipant(cursor.publicId(), username)
                        .orElseThrow(() -> new BadRequestException("Cursor no válido"));
        LocalDateTime beforeAt = cursor == null ? null : cursor.order();

        int pageSize = Math.clamp(limit, 1, MAX_CHAT_PAGE);
        // One extra row tells whether there is another page
        Limit fetch = Limit.of(pageSize + 1);
        List<ChatSummaryDto> rows;
        if (beforeId == null) {
            rows = conversationRepository.findChatsForUser(username, fetch);
        } else if (beforeAt != null) {
            rows = conversationRepository.findChatsForUserBefore(username, beforeAt, beforeId, fetch);
        } else {
            rows = conversationRepository.findChatsWithoutMessagesForUserBefore(username, beforeId, fetch);
        }

        boolean hasMore = rows.size() > pageSize;
        List<ChatSummaryDto> page = List.copyOf(rows.subList(0, Math.min(pageSize, rows.size())));
        ChatSummaryDto last = hasMore ? page.get(page.size() - 1) : null;

        return new ChatPageDto(
            page,
            hasMore,
            last != null ? OpaqueCursor.encode(OpaqueCursor.CHAT, last.lastMessageAt(), last.conversationId()) : null,
            participantRepository.countUnreadChats(username)
        );
    }
}