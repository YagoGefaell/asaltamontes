package io.github.yagogefaell.asaltamontes.message.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
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

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserActionLimiter limiter;

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
        participantRepository.addIfAbsent(chatId, userId, now());
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

    private static LocalDateTime now() {
        return OpaqueCursor.truncate(LocalDateTime.now());
    }
}
