package io.github.yagogefaell.asaltamontes.message.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityModerationLog;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.domain.ModerationAction;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityModerationLogRepository;
import io.github.yagogefaell.asaltamontes.community.dto.ChatAccess;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityChatDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.message.domain.ChatType;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ManagedBy;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/**
 * The chat of each community (T20): a group conversation managed by the community ({@code managed_by = COMMUNITY}),
 * whose participants are kept in sync with the members. Everybody is a plain participant: nobody adds, expels or
 * renames by hand (the community does, and the chat follows). New participants read the whole history (decision of
 * Yago, 2026-10-27). Muted members read but cannot write; expelled ones lose the chat; the moderation can switch
 * the chat off.
 *
 * <p>The chat is created lazily (first membership change or first access) and once for all existing communities by
 * the migration. Every method runs in the transaction of the membership change, so chat and members never drift
 * apart; the sync is a handful of statements, never one query per member.
 */
@Service
@RequiredArgsConstructor
public class CommunityChatService {

    private static final String NOT_FOUND = "Comunidad no encontrada";
    private static final String CANNOT_DELETE = "No puedes borrar mensajes de este chat";

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserActionLimiter limiter;
    private final CommunityModerationLogRepository moderationLogRepository;

    /** The chat of a community she belongs to (created if missing); everybody else gets the 404 of a missing community. */
    @Transactional
    public CommunityChatDto open(UUID communityPublicId, UserAccount user) {
        Long communityId = communityRepository.findIdByPublicId(communityPublicId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        ChatAccess access = memberRepository.findChatAccess(communityId, user.getId())
                .filter(a -> a.status() != MemberStatus.BANNED)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        // Also repairs a missing participation (e.g. a community that predates the chat)
        addMember(communityId, user.getId());
        UUID chatId = conversationRepository.findCommunityChatPublicId(communityId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return new CommunityChatDto(chatId, access.chatEnabled(), access.chatEnabled() && access.status() != MemberStatus.MUTED);
    }

    /** Id of the community's chat; creates it with the current members if it does not exist yet. */
    @Transactional
    public Long ensureChat(Long communityId) {
        return conversationRepository.findCommunityChatId(communityId).orElseGet(() -> create(communityId));
    }

    private Long create(Long communityId) {
        // The community row is locked, so two requests creating the chat at once end up with one
        Community community = communityRepository.lockById(communityId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return conversationRepository.findCommunityChatId(communityId).orElseGet(() -> {
            Conversation chat = new Conversation();
            chat.setType(ChatType.GROUP);
            chat.setGroupName(community.getName());
            chat.setGroupImage(community.getImageUrl());
            chat.setManagedBy(ManagedBy.COMMUNITY);
            chat.setManagedRefId(communityId);
            chat.setCreatedAt(now());
            Conversation saved = conversationRepository.saveAndFlush(chat);
            participantRepository.addCommunityMembers(saved.getId(), communityId, now());
            return saved.getId();
        });
    }

    /** She joined the community (or was readmitted, or accepted): she is in the chat. Call after the member row is flushed. */
    @Transactional
    public Long addMember(Long communityId, Long userId) {
        Long chatId = ensureChat(communityId);
        participantRepository.addCommunityMemberIfAbsent(chatId, communityId, userId, now());
        return chatId;
    }

    /** She left or was expelled: she is out of the chat and her sessions drop it from the list. */
    @Transactional
    public void removeMember(Long communityId, Long userId, String reason) {
        Long chatId = conversationRepository.findCommunityChatId(communityId).orElse(null);
        if (chatId != null && participantRepository.deleteByConversationIdAndUserId(chatId, userId) > 0) {
            conversationRepository.findCommunityChatPublicId(communityId)
                    .ifPresent(publicId -> eventPublisher.publishEvent(new ConversationRemovedEvent(userId, publicId, reason)));
        }
    }

    /** The community was renamed or changed its image: the chat shows the same. */
    @Transactional
    public void syncProfile(Community community) {
        conversationRepository.updateCommunityChatProfile(community.getId(), community.getName(), community.getImageUrl());
    }

    /** The community is deleted: its chat goes away with the messages, and every participant's list is updated. */
    @Transactional
    public void deleteChat(Long communityId) {
        Long chatId = conversationRepository.findCommunityChatId(communityId).orElse(null);
        if (chatId == null) {
            return;
        }
        UUID publicId = conversationRepository.findCommunityChatPublicId(communityId).orElseThrow();
        List<Long> participants = participantRepository.findUserIdsByConversationId(chatId);
        messageRepository.deleteByConversationId(chatId);
        participantRepository.deleteByConversationId(chatId);
        conversationRepository.deleteById(chatId);
        participants.forEach(userId -> eventPublisher.publishEvent(new ConversationRemovedEvent(userId, publicId, "REMOVED")));
    }

    /**
     * Whether she may write in this conversation if a community manages it (nothing to check otherwise): the chat has
     * to be on, she must not be muted (read only), and a per-user flood limit applies because every message reaches
     * all the members.
     */
    @Transactional(readOnly = true)
    public void requireCanWrite(Long conversationId, Long userId) {
        Long communityId = conversationRepository.findManagingCommunityId(conversationId).orElse(null);
        if (communityId == null) {
            return;
        }
        ChatAccess access = memberRepository.findChatAccess(communityId, userId)
                .filter(a -> a.status() != MemberStatus.BANNED)
                .orElseThrow(() -> new NotFoundException("Chat no encontrado"));
        if (!access.chatEnabled()) {
            throw new ConflictException("El chat de esta comunidad está desactivado");
        }
        if (access.status() == MemberStatus.MUTED) {
            throw new ForbiddenException("Estás silenciada en esta comunidad y no puedes escribir");
        }
        limiter.communityMessage(userId);
    }

    /**
     * Moderation of the chat of a community (T20c): the owner and the moderators delete other members' messages.
     * Returns the id of the community, and 403 for anyone else or for a chat that no community manages.
     */
    @Transactional(readOnly = true)
    public Long requireModerator(Long conversationId, Long userId) {
        Long communityId = conversationRepository.findManagingCommunityId(conversationId)
                .orElseThrow(() -> new ForbiddenException(CANNOT_DELETE));
        if (!memberRepository.isModerator(communityId, userId)) {
            throw new ForbiddenException(CANNOT_DELETE);
        }
        return communityId;
    }

    /**
     * Same hierarchy as muting: a moderator deletes messages of plain members (or of someone who left), not those of the
     * owner or of other moderators; the owner deletes anyone's.
     */
    @Transactional(readOnly = true)
    public void requireCanDeleteFrom(Long communityId, Long actorId, Long authorId) {
        if (authorId == null || authorId.equals(actorId)) {
            return;
        }
        boolean actorIsOwner = memberRepository.isOwner(communityId, actorId);
        if (!actorIsOwner && memberRepository.isModerator(communityId, authorId)) {
            throw new ForbiddenException("Solo la propietaria puede borrar mensajes de la moderación");
        }
    }

    /**
     * Fills {@code authorRole} (T20e) in the messages of a chat that a community manages: one query for the whole page,
     * whatever its size. Other chats are returned as they are.
     */
    @Transactional(readOnly = true)
    public List<MessageDto> withAuthorRoles(Long conversationId, List<MessageDto> messages) {
        Collection<String> authors = messages.stream().map(MessageDto::senderUsername).filter(u -> u != null).distinct().toList();
        if (authors.isEmpty()) {
            return messages;
        }
        Long communityId = conversationRepository.findManagingCommunityId(conversationId).orElse(null);
        if (communityId == null) {
            return messages;
        }
        Map<String, CommunityRole> roles = new HashMap<>();
        memberRepository.findStaffRoles(communityId, authors).forEach(r -> roles.put(r.getUsername(), r.getRole()));
        if (roles.isEmpty()) {
            return messages;
        }
        return messages.stream()
                .map(m -> m.senderUsername() == null ? m : m.withAuthorRole(roles.get(m.senderUsername())))
                .toList();
    }

    /** The role badge of one author (T20e), for the message that is being sent. */
    @Transactional(readOnly = true)
    public CommunityRole authorRole(Long conversationId, String username) {
        return conversationRepository.findManagingCommunityId(conversationId)
                .flatMap(communityId -> memberRepository.findStaffRoles(communityId, List.of(username)).stream()
                        .map(CommunityMemberRepository.StaffRole::getRole).findFirst())
                .orElse(null);
    }

    /** Leaves a trace of a deleted message in the moderation log of the community. */
    @Transactional
    public void logMessageDeleted(Long communityId, Long actorId, Long authorId) {
        moderationLogRepository.save(new CommunityModerationLog(communityId, actorId, authorId, ModerationAction.MESSAGE_DELETED, null));
    }

    private static LocalDateTime now() {
        return OpaqueCursor.truncate(LocalDateTime.now());
    }
}
