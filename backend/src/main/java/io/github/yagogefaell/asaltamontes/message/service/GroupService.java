package io.github.yagogefaell.asaltamontes.message.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import io.github.yagogefaell.asaltamontes.message.domain.ChatType;
import io.github.yagogefaell.asaltamontes.message.domain.Conversation;
import io.github.yagogefaell.asaltamontes.message.domain.ConversationParticipant;
import io.github.yagogefaell.asaltamontes.message.domain.ManagedBy;
import io.github.yagogefaell.asaltamontes.message.domain.Message;
import io.github.yagogefaell.asaltamontes.message.domain.MessageType;
import io.github.yagogefaell.asaltamontes.message.domain.ParticipantRole;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.message.dto.GroupDetailsDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.dto.ParticipantDto;
import io.github.yagogefaell.asaltamontes.message.dto.UpdateGroupDto;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.repository.MessageRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import io.github.yagogefaell.asaltamontes.user.service.UserService;

/**
 * Management of group chats (T12): details, rename / image, add and expel members, roles, leaving. Every change
 * leaves a system message in the chat (no sender, structured payload) that goes through the usual broadcaster.
 * <p>
 * Operations that count or change the members lock the conversation row first ({@code PESSIMISTIC_WRITE}), so two
 * admins adding people at the same time cannot exceed the size cap and two admins leaving at the same time cannot
 * leave the group without admin. Chats that are not groups, chats that do not exist and chats the user is not in
 * all answer the same 404, so nothing can be probed.
 */
@Service
public class GroupService {

    public static final int MAX_PARTICIPANT_PAGE = 50;
    public static final int DEFAULT_PARTICIPANT_PAGE = 20;
    static final String NOT_FOUND = "Chat no encontrado";
    static final String MANAGED = "Este grupo se gestiona automáticamente";

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final MediaReferenceService mediaReferences;
    private final ApplicationEventPublisher eventPublisher;
    private final CommunityMemberRepository communityMemberRepository;
    private final UserActionLimiter limiter;
    private final int maxGroupSize;

    public GroupService(ConversationRepository conversationRepository,
                        ConversationParticipantRepository participantRepository,
                        MessageRepository messageRepository,
                        UserService userService,
                        BlockService blockService,
                        MediaReferenceService mediaReferences,
                        ApplicationEventPublisher eventPublisher,
                        CommunityMemberRepository communityMemberRepository,
                        UserActionLimiter limiter,
                        @Value("${asaltamontes.chat.max-group-size:100}") int maxGroupSize) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userService = userService;
        this.blockService = blockService;
        this.mediaReferences = mediaReferences;
        this.eventPublisher = eventPublisher;
        this.communityMemberRepository = communityMemberRepository;
        this.limiter = limiter;
        this.maxGroupSize = maxGroupSize;
    }

    /** The conversation and the caller's own participation, once the permission checks passed. */
    private record Access(Conversation conversation, ConversationParticipant me) {
    }

    @Transactional(readOnly = true)
    public GroupDetailsDto getDetails(UUID conversationId, UserAccount user, int page, int size) {
        Access access = access(conversationId, user.getId(), false, false);
        return details(access, page, size);
    }

    @Transactional
    public GroupDetailsDto update(UUID conversationId, UpdateGroupDto changes, UserAccount user) {
        Access access = access(conversationId, user.getId(), true, true);
        Conversation conversation = access.conversation();
        // A community chat takes its name and image from the community
        requireManual(conversation);
        LocalDateTime now = now();
        String actor = user.getUsername();

        if (changes.has("groupName")) {
            String name = changes.groupName() == null ? "" : changes.groupName().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("El nombre del grupo es obligatorio");
            }
            if (name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {
                throw new BadRequestException("El nombre del grupo contiene caracteres no permitidos");
            }
            if (!name.equals(conversation.getGroupName())) {
                conversation.setGroupName(name);
                postSystem(conversation, user.getId(), "GROUP_RENAMED", actor + " cambió el nombre del grupo a «" + name + "»",
                        payload("GROUP_RENAMED", actor, null, Map.of("name", name)), now);
            }
        }

        if (changes.has("groupImage") || changes.has("groupMediaId")) {
            // A null value removes the image; otherwise it has to be one of the admin's own uploads
            MediaReferenceService.Linked image = mediaReferences.link(changes.groupMediaId(), changes.groupImage(),
                    MediaPurpose.GROUP_IMAGE, user.getId(), conversation.getGroupImage());
            boolean changed = !java.util.Objects.equals(image.mediaId(), conversation.getGroupMediaId())
                    || !java.util.Objects.equals(image.url(), conversation.getGroupImage());
            if (changed) {
                conversation.setGroupMediaId(image.mediaId());
                conversation.setGroupImage(image.url());
                boolean removed = image.url() == null;
                postSystem(conversation, user.getId(), "GROUP_IMAGE_CHANGED",
                        actor + (removed ? " quitó la imagen del grupo" : " cambió la imagen del grupo"),
                        payload("GROUP_IMAGE_CHANGED", actor, null, Map.of("removed", removed)), now);
            }
        }

        if (changes.has("historyVisibleToNew")) {
            if (changes.historyVisibleToNew() == null) {
                throw new BadRequestException("El historial visible debe ser verdadero o falso");
            }
            requireManual(conversation);
            if (changes.historyVisibleToNew() != conversation.isHistoryVisibleToNew()) {
                boolean visible = changes.historyVisibleToNew();
                conversation.setHistoryVisibleToNew(visible);
                postSystem(conversation, user.getId(), "HISTORY_VISIBILITY_CHANGED",
                        actor + (visible ? " activó" : " desactivó") + " el historial visible para las nuevas participantes",
                        payload("HISTORY_VISIBILITY_CHANGED", actor, null, Map.of("visible", visible)), now);
            }
        }
        conversationRepository.save(conversation);
        return details(access, 0, DEFAULT_PARTICIPANT_PAGE);
    }

    @Transactional
    public GroupDetailsDto addParticipants(UUID conversationId, List<String> usernames, UserAccount user) {
        Access access = access(conversationId, user.getId(), true, true);
        Conversation conversation = access.conversation();
        requireManual(conversation);
        // BUG-071: every add notifies the new members, so adding and expelling in a loop cannot be unlimited
        limiter.groupMemberAdd(user.getId());

        Set<String> wanted = normalize(usernames, user.getUsername());
        if (wanted.isEmpty()) {
            throw new BadRequestException("Debes indicar al menos una usuaria válida distinta de ti");
        }
        Map<String, UserAccount> found = userService.loadUsersByUsernames(wanted).stream()
                .collect(Collectors.toMap(u -> u.getUsername().toLowerCase(Locale.ROOT), u -> u, (a, b) -> a));
        for (String username : wanted) {
            if (!found.containsKey(username)) {
                // Nothing is added if any of them does not exist
                throw new NotFoundException("Usuaria no encontrada: " + username);
            }
        }

        List<Long> already = participantRepository.findParticipantUserIds(conversation.getId(),
                found.values().stream().map(UserAccount::getId).toList());
        List<UserAccount> toAdd = wanted.stream().map(found::get).filter(u -> !already.contains(u.getId())).toList();
        if (toAdd.isEmpty()) {
            return details(access, 0, DEFAULT_PARTICIPANT_PAGE);
        }
        // T13: nothing is added if any of them blocked the caller or was blocked by her (neutral message, one query)
        List<Long> toAddIds = toAdd.stream().map(UserAccount::getId).toList();
        // Whoever blocked the caller looks like a user that does not exist, so nobody can probe who blocked her
        Set<Long> blockers = blockService.blockersAmong(user.getId(), toAddIds);
        for (UserAccount account : toAdd) {
            if (blockers.contains(account.getId())) {
                throw new NotFoundException("Usuaria no encontrada: " + account.getUsername().toLowerCase(Locale.ROOT));
            }
        }
        // T13: no block either way and each one's dm_policy, with one neutral 403
        blockService.requireCanAddToGroup(user.getId(), toAddIds);
        // The row is locked, so this count is the real one for the whole transaction
        if (participantRepository.countByConversationId(conversation.getId()) + toAdd.size() > maxGroupSize) {
            throw new BadRequestException("Un grupo puede tener como máximo " + maxGroupSize + " participantes");
        }

        // The "added" message and the new members share a timestamp: they see who added them, nothing before
        LocalDateTime now = now();
        List<ConversationParticipant> participants = new ArrayList<>();
        for (UserAccount account : toAdd) {
            ConversationParticipant participant = new ConversationParticipant();
            participant.setConversation(conversation);
            participant.setUser(account);
            participant.setJoinedAt(now);
            participants.add(participant);
        }
        participantRepository.saveAll(participants);

        List<String> targets = toAdd.stream().map(UserAccount::getUsername).toList();
        postSystem(conversation, user.getId(), "MEMBER_ADDED", user.getUsername() + " añadió a " + String.join(", ", targets),
                payload("MEMBER_ADDED", user.getUsername(), targets, null), now);
        eventPublisher.publishEvent(new AddedToGroup(conversation.getPublicId(), conversation.getGroupName(), user.getId(),
                user.getUsername(), toAdd.stream().map(UserAccount::getId).toList()));
        return details(access, 0, DEFAULT_PARTICIPANT_PAGE);
    }

    @Transactional
    public void removeParticipant(UUID conversationId, String username, UserAccount user) {
        Access access = access(conversationId, user.getId(), true, true);
        Conversation conversation = access.conversation();
        requireManual(conversation);

        ConversationParticipant target = target(conversation, username);
        if (target.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Para salir del grupo usa «Salir del grupo»");
        }
        UserAccount removed = target.getUser();
        requireNotCreator(conversation, removed, user);
        participantRepository.delete(target);
        participantRepository.flush();

        postSystem(conversation, user.getId(), "MEMBER_REMOVED", user.getUsername() + " expulsó a " + removed.getUsername(),
                payload("MEMBER_REMOVED", user.getUsername(), List.of(removed.getUsername()), null), now());
        eventPublisher.publishEvent(new ConversationRemovedEvent(removed.getId(), conversation.getPublicId(), "REMOVED"));
    }

    @Transactional
    public void changeRole(UUID conversationId, String username, ParticipantRole role, UserAccount user) {
        Access access = access(conversationId, user.getId(), true, true);
        Conversation conversation = access.conversation();
        requireManual(conversation);

        ConversationParticipant target = target(conversation, username);
        if (target.getRole() == role) {
            return;
        }
        requireNotCreator(conversation, target.getUser(), user);
        if (role == ParticipantRole.MEMBER && participantRepository.countAdmins(conversation.getId()) <= 1) {
            throw new ConflictException("El grupo debe tener al menos una administradora");
        }
        target.setRole(role);
        participantRepository.save(target);
        String name = target.getUser().getUsername();
        postSystem(conversation, user.getId(), "ROLE_CHANGED",
                user.getUsername() + (role == ParticipantRole.ADMIN ? " hizo administradora a " : " quitó el rol de administradora a ") + name,
                payload("ROLE_CHANGED", user.getUsername(), List.of(name), Map.of("role", role.name())), now());
    }

    @Transactional
    public void leave(UUID conversationId, UserAccount user) {
        Access access = access(conversationId, user.getId(), true, false);
        Conversation conversation = access.conversation();
        ConversationParticipant me = access.me();
        // The chat of a community follows the membership: she leaves the community instead (T20)
        requireManual(conversation);

        String promoted = null;
        if (me.getRole() == ParticipantRole.ADMIN && participantRepository.countAdmins(conversation.getId()) <= 1) {
            // The oldest member takes over; if there is nobody else the group is deleted below
            List<ConversationParticipant> next = participantRepository.findOldestExcluding(conversation.getId(), user.getId(), Limit.of(1));
            if (!next.isEmpty()) {
                next.get(0).setRole(ParticipantRole.ADMIN);
                participantRepository.save(next.get(0));
                promoted = next.get(0).getUser().getUsername();
            }
        }
        participantRepository.delete(me);
        participantRepository.flush();

        if (participantRepository.countByConversationId(conversation.getId()) == 0) {
            // The last one left: the group goes away with its messages (its image becomes an orphan for the cleanup job)
            messageRepository.deleteByConversationId(conversation.getId());
            conversationRepository.delete(conversation);
        } else {
            LocalDateTime now = now();
            postSystem(conversation, user.getId(), "MEMBER_LEFT", user.getUsername() + " salió del grupo",
                    payload("MEMBER_LEFT", user.getUsername(), null, null), now);
            if (promoted != null) {
                postSystem(conversation, user.getId(), "ADMIN_ASSIGNED", promoted + " es ahora administradora del grupo",
                        payload("ADMIN_ASSIGNED", null, List.of(promoted), null), now);
            }
        }
        // Her other sessions drop the chat from the list too
        eventPublisher.publishEvent(new ConversationRemovedEvent(user.getId(), conversation.getPublicId(), "LEFT"));
    }

    // --- helpers ---

    /**
     * Loads the group for a user who takes part in it. {@code write} locks the conversation row (after checking
     * membership, so outsiders cannot make admins wait) and re-reads her participation under the lock.
     */
    private Access access(UUID publicId, Long userId, boolean write, boolean adminOnly) {
        Long id = publicId == null ? null : conversationRepository.findIdByPublicId(publicId).orElse(null);
        if (id == null || !participantRepository.existsByConversationIdAndUserId(id, userId)) {
            throw new NotFoundException(NOT_FOUND);
        }
        Conversation conversation = (write ? conversationRepository.findByPublicIdForUpdate(publicId)
                : conversationRepository.findByPublicId(publicId)).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        ConversationParticipant me = participantRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        if (conversation.getType() != ChatType.GROUP) {
            throw new NotFoundException(NOT_FOUND);
        }
        if (adminOnly && me.getRole() != ParticipantRole.ADMIN) {
            throw new ForbiddenException("Solo las administradoras del grupo pueden hacerlo");
        }
        return new Access(conversation, me);
    }

    /** The creator cannot be expelled or demoted by another admin (security review of #82). */
    private static void requireNotCreator(Conversation conversation, UserAccount target, UserAccount actor) {
        UserAccount creator = conversation.getCreatedBy();
        if (creator != null && creator.getId().equals(target.getId()) && !creator.getId().equals(actor.getId())) {
            throw new ForbiddenException("Solo la creadora del grupo puede hacerlo con ella");
        }
    }

    private static void requireManual(Conversation conversation) {
        if (conversation.getManagedBy() != ManagedBy.NONE) {
            throw new ConflictException(MANAGED);
        }
    }

    private ConversationParticipant target(Conversation conversation, String username) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return participantRepository.findByConversationIdAndLowerUsername(conversation.getId(), name)
                .orElseThrow(() -> new NotFoundException("Participante no encontrada"));
    }

    /** Trimmed, lower-cased, without blanks, duplicates or the caller herself. */
    private static Set<String> normalize(List<String> usernames, String self) {
        Set<String> result = new LinkedHashSet<>();
        if (usernames != null) {
            for (String username : usernames) {
                if (username == null || username.isBlank()) {
                    continue;
                }
                String name = username.trim().toLowerCase(Locale.ROOT);
                if (!name.equals(self.toLowerCase(Locale.ROOT))) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private GroupDetailsDto details(Access access, int page, int size) {
        Conversation conversation = access.conversation();
        int pageSize = Math.clamp(size, 1, MAX_PARTICIPANT_PAGE);
        Page<ParticipantDto> participants = participantRepository.findParticipantPage(conversation.getId(),
                PageRequest.of(Math.max(page, 0), pageSize));
        CommunityRole moderationRole = moderationRole(conversation, access.me().getUser());
        return new GroupDetailsDto(
                conversation.getPublicId(),
                conversation.getGroupName(),
                conversation.getGroupImage(),
                conversation.getGroupMediaId(),
                (int) participants.getTotalElements(),
                access.me().getRole(),
                conversation.isHistoryVisibleToNew(),
                conversation.getManagedBy() != ManagedBy.NONE,
                moderationRole != null,
                moderationRole,
                participants);
    }

    /** Owner or moderator of the community that manages the chat: the ones who may try to delete messages (T20d, T20e). */
    private CommunityRole moderationRole(Conversation conversation, UserAccount user) {
        if (conversation.getManagedBy() != ManagedBy.COMMUNITY) {
            return null;
        }
        return communityMemberRepository.findStaffRoles(conversation.getManagedRefId(), List.of(user.getUsername())).stream()
                .map(CommunityMemberRepository.StaffRole::getRole).findFirst().orElse(null);
    }

    private static Map<String, Object> payload(String action, String actor, List<String> targets, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        if (actor != null) {
            payload.put("actor", actor);
        }
        if (targets != null) {
            payload.put("targets", targets);
        }
        if (extra != null) {
            payload.putAll(extra);
        }
        return payload;
    }

    private static LocalDateTime now() {
        return OpaqueCursor.truncate(LocalDateTime.now());
    }

    /**
     * Saves a system message, makes it the chat's last message, counts it as unread for everybody but the actor and
     * publishes it (the broadcaster sends it to the members that remain once the transaction commits).
     */
    private void postSystem(Conversation conversation, Long actorId, String action, String content,
                            Map<String, Object> payload, LocalDateTime at) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setType(MessageType.SYSTEM);
        message.setContent(content);
        message.setSystemPayload(payload);
        message.setSentAt(at);
        Message saved = messageRepository.save(message);

        conversationRepository.updateLastMessage(conversation.getId(), MessageService.preview(content), at, null);
        participantRepository.incrementUnreadExceptSender(conversation.getId(), actorId);
        eventPublisher.publishEvent(new MessageSentEvent(new MessageDto(saved.getPublicId(), conversation.getPublicId(),
                null, content, at, MessageType.SYSTEM, payload)));
    }
}
