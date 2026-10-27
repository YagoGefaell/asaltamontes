package io.github.yagogefaell.asaltamontes.community.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityModerationLog;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.domain.ModerationAction;
import io.github.yagogefaell.asaltamontes.community.dto.BannedMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.community.dto.CursorPageDto;
import io.github.yagogefaell.asaltamontes.community.dto.JoinRequestDto;
import io.github.yagogefaell.asaltamontes.community.dto.ModerationLogEntryDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityJoinRequestRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityModerationLogRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.service.MediaCleanupService;
import io.github.yagogefaell.asaltamontes.message.service.CommunityChatService;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

/**
 * Moderation of a community (T19): edit, roles, mute, expel ("Expulsadas") and readmit, ownership transfer, requests
 * to join a private community, and the moderation log.
 *
 * <p>Rules: only the owner and the moderators act, and anyone else (also a banned moderator) gets the same 404 as for
 * a community that does not exist. The owner can act on everybody but herself; a moderator only on plain members.
 * The owner can never be expelled or muted. Every action is written to the log in the same transaction and, when it
 * changes who counts as a member (expel, readmit, accept), {@code memberCount} moves in that same transaction.
 * The member row is locked while it changes, so two moderators acting at once cannot count one expulsion twice.
 */
@Service
@RequiredArgsConstructor
public class CommunityModerationService {

    public static final int MAX_PAGE = 50;
    public static final int DEFAULT_PAGE = 20;

    private static final String NOT_FOUND = "Comunidad no encontrada";
    private static final String NO_MEMBER = "Usuaria no encontrada en esta comunidad";
    private static final int NAME_MAX = 100;
    private static final int DESCRIPTION_MAX = 500;
    private static final int PLACE_MAX = 80;

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final CommunityModerationLogRepository logRepository;
    private final CommunityJoinRequestRepository joinRequestRepository;
    private final UserAccountRepository userAccountRepository;
    private final MediaReferenceService mediaReferences;
    private final MediaCleanupService mediaCleanup;
    private final ApplicationEventPublisher eventPublisher;
    private final CommunityChatService communityChat;

    // ---- Edit the community ----

    /**
     * PATCH (D-020): an absent field stays, an explicit null clears it if optional. The owner and the moderators edit
     * name, description, image and place; only the owner changes the visibility. The slug does not change on rename,
     * so shared links keep working.
     */
    @Transactional
    public CommunityDetailsDto update(String ref, JsonNode body, UserAccount user) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("La petición no es válida");
        }
        Long communityId = resolve(ref);
        CommunityMember actor = requireStaff(communityId, user);
        Community community = communityRepository.lockById(communityId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        List<String> changed = new ArrayList<>();

        if (body.has("name")) {
            String name = SlugGenerator.normalizeName(requiredText(body, "name", NAME_MAX));
            if (name.isBlank()) {
                throw new BadRequestException("El nombre de la comunidad es obligatorio");
            }
            if (!name.equalsIgnoreCase(community.getName()) && communityRepository.existsByNameIgnoreCase(name)) {
                throw new ConflictException("Ya existe una comunidad con ese nombre");
            }
            if (!name.equals(community.getName())) {
                community.setName(name);
                changed.add("name");
            }
        }
        if (body.has("description")) {
            community.setDescription(optionalText(body, "description", DESCRIPTION_MAX));
            changed.add("description");
        }
        if (body.has("city")) {
            community.setCity(optionalText(body, "city", PLACE_MAX));
            changed.add("city");
        }
        if (body.has("province")) {
            community.setProvince(optionalText(body, "province", PLACE_MAX));
            changed.add("province");
        }
        if (body.has("visibility")) {
            CommunityVisibility visibility = visibility(body.get("visibility"));
            if (visibility != community.getVisibility()) {
                if (actor.getRole() != CommunityRole.OWNER) {
                    throw new ForbiddenException("Solo la propietaria puede cambiar la visibilidad");
                }
                community.setVisibility(visibility);
                changed.add("visibility");
            }
        }
        if (body.has("chatEnabled")) {
            // The chat can be switched off (history stays readable) and on again
            if (!body.get("chatEnabled").isBoolean()) {
                throw new BadRequestException("chatEnabled debe ser verdadero o falso");
            }
            if (body.get("chatEnabled").asBoolean() != community.isChatEnabled()) {
                community.setChatEnabled(body.get("chatEnabled").asBoolean());
                changed.add("chatEnabled");
            }
        }
        if (body.has("coverMediaId") || body.has("imageUrl")) {
            linkCover(community, body, user);
            changed.add("image");
        }

        if (!changed.isEmpty()) {
            communityRepository.save(community);
            communityChat.syncProfile(community);
            log(communityId, user.getId(), null, ModerationAction.COMMUNITY_UPDATED, String.join(",", changed));
        }
        return details(community, actor);
    }

    private void linkCover(Community community, JsonNode body, UserAccount user) {
        UUID previous = community.getCoverMediaId();
        String legacyExternal = previous == null ? community.getImageUrl() : null;
        UUID mediaId = null;
        if (body.hasNonNull("coverMediaId")) {
            try {
                mediaId = UUID.fromString(body.get("coverMediaId").asText());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Identificador de imagen no válido");
            }
        }
        String url = body.hasNonNull("imageUrl") ? body.get("imageUrl").asText() : null;
        if (url != null && url.length() > 255) {
            throw new BadRequestException("La URL de la imagen no puede superar los 255 caracteres");
        }
        MediaReferenceService.Linked cover = mediaReferences.link(mediaId, url, MediaPurpose.COMMUNITY_COVER, user.getId(), legacyExternal);
        community.setCoverMediaId(cover.mediaId());
        community.setImageUrl(cover.url());
        if (previous != null && !previous.equals(cover.mediaId())) {
            mediaCleanup.deleteIfUnreferenced(previous, null);
        }
    }

    private CommunityDetailsDto details(Community c, CommunityMember actor) {
        return new CommunityDetailsDto(c.getPublicId(), c.getName(), c.getSlug(), c.getDescription(), c.getImageUrl(),
                actor.getRole(), c.getMemberCount(), c.getVisibility(), c.getCity(), c.getProvince(), false);
    }

    // ---- Roles, mute, ban, ownership ----

    /** Owner only. {@code role} is MODERATOR or MEMBER. */
    @Transactional
    public void changeRole(UUID communityPublicId, String username, CommunityRole role, UserAccount user) {
        Long communityId = internalId(communityPublicId);
        if (role != CommunityRole.MODERATOR && role != CommunityRole.MEMBER) {
            throw new BadRequestException("El rol debe ser MODERATOR o MEMBER");
        }
        Acting acting = lockActingPair(communityId, user, username);
        if (acting.actor().getRole() != CommunityRole.OWNER) {
            throw new ForbiddenException("Solo la propietaria puede cambiar los roles");
        }
        CommunityMember target = acting.target();
        rejectSelfOrOwner(target, user, "No se puede cambiar el rol de la propietaria");
        requireNotBanned(target);
        if (target.getRole() == role) {
            return;
        }
        target.setRole(role);
        log(communityId, user.getId(), target.getUserAccount().getId(),
                role == CommunityRole.MODERATOR ? ModerationAction.PROMOTE : ModerationAction.DEMOTE, null);
    }

    @Transactional
    public void mute(UUID communityPublicId, String username, String reason, UserAccount user) {
        setMuted(communityPublicId, username, reason, user, true);
    }

    @Transactional
    public void unmute(UUID communityPublicId, String username, UserAccount user) {
        setMuted(communityPublicId, username, null, user, false);
    }

    private void setMuted(UUID communityPublicId, String username, String reason, UserAccount user, boolean muted) {
        Long communityId = internalId(communityPublicId);
        CommunityMember target = lockActingPair(communityId, user, username).target();
        authorize(communityId, user, target, "No se puede silenciar a la propietaria");
        requireNotBanned(target);
        MemberStatus wanted = muted ? MemberStatus.MUTED : MemberStatus.ACTIVE;
        if (target.getStatus() == wanted) {
            return;
        }
        target.setStatus(wanted);
        log(communityId, user.getId(), target.getUserAccount().getId(),
                muted ? ModerationAction.MUTE : ModerationAction.UNMUTE, cleanReason(reason));
    }

    /**
     * Expels a member: BANNED, back to a plain member role (so a readmission does not return her powers), out of the
     * count (same transaction) and in the log with the reason. Repeating it changes nothing.
     */
    @Transactional
    public void ban(UUID communityPublicId, String username, String reason, UserAccount user) {
        Long communityId = internalId(communityPublicId);
        Acting acting = lockActingPair(communityId, user, username);
        CommunityMember target = acting.target();
        authorize(communityId, user, target, "No se puede expulsar a la propietaria");
        if (target.getStatus() == MemberStatus.BANNED) {
            return;
        }
        target.setStatus(MemberStatus.BANNED);
        target.setRole(CommunityRole.MEMBER);
        // Kept for the hierarchy: whoever the owner expels, a moderator cannot readmit
        target.setBannedBy(user.getId());
        target.setBannedByRole(acting.actor().getRole());
        communityRepository.decrementMemberCount(communityId);
        // Expelled: out of the chat too
        communityChat.removeMember(communityId, target.getUserAccount().getId(), "REMOVED");
        log(communityId, user.getId(), target.getUserAccount().getId(), ModerationAction.BAN, cleanReason(reason));
    }

    /**
     * Readmits an expelled member as an active one (back in the count, same transaction). Not banned: 404. Whoever the
     * owner expelled can only be readmitted by the owner; the owner can readmit anyone.
     */
    @Transactional
    public void unban(UUID communityPublicId, String username, UserAccount user) {
        Long communityId = internalId(communityPublicId);
        Acting acting = lockActingPair(communityId, user, username);
        CommunityMember target = acting.target();
        if (target.getStatus() != MemberStatus.BANNED) {
            throw new NotFoundException(NO_MEMBER);
        }
        if (target.getBannedByRole() == CommunityRole.OWNER && acting.actor().getRole() != CommunityRole.OWNER) {
            throw new ForbiddenException("Solo la propietaria puede readmitir a quien ella expulsó");
        }
        target.setStatus(MemberStatus.ACTIVE);
        target.setBannedBy(null);
        target.setBannedByRole(null);
        communityRepository.incrementMemberCount(communityId);
        memberRepository.flush();
        communityChat.addMember(communityId, target.getUserAccount().getId());
        log(communityId, user.getId(), target.getUserAccount().getId(), ModerationAction.UNBAN, null);
    }

    /** Owner only: the target becomes OWNER and the previous owner a moderator. */
    @Transactional
    public void transferOwnership(UUID communityPublicId, String username, UserAccount user) {
        Long communityId = internalId(communityPublicId);
        Acting acting = lockActingPair(communityId, user, username);
        if (acting.actor().getRole() != CommunityRole.OWNER) {
            throw new ForbiddenException("Solo la propietaria puede transferir la propiedad");
        }
        CommunityMember target = acting.target();
        rejectSelfOrOwner(target, user, "Ya eres la propietaria");
        requireNotBanned(target);
        target.setRole(CommunityRole.OWNER);
        target.setStatus(MemberStatus.ACTIVE);
        acting.actor().setRole(CommunityRole.MODERATOR);
        log(communityId, user.getId(), target.getUserAccount().getId(), ModerationAction.TRANSFER_OWNERSHIP, null);
    }

    // ---- Requests to join ----

    @Transactional
    public void approveRequest(UUID communityPublicId, String username, UserAccount user) {
        decide(communityPublicId, username, user, true);
    }

    @Transactional
    public void rejectRequest(UUID communityPublicId, String username, UserAccount user) {
        decide(communityPublicId, username, user, false);
    }

    private void decide(UUID communityPublicId, String username, UserAccount staff, boolean approve) {
        Long communityId = internalId(communityPublicId);
        requireStaff(communityId, staff);
        UserAccount requester = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        // The delete says who decides first: a second moderator at the same moment finds nothing
        if (joinRequestRepository.deleteRequest(communityId, requester.getId()) == 0) {
            throw new NotFoundException("Solicitud no encontrada");
        }
        Community community = communityRepository.getReferenceById(communityId);
        if (approve && memberRepository.findByCommunityIdAndUserAccountId(communityId, requester.getId()).isEmpty()) {
            memberRepository.saveAndFlush(new CommunityMember(community, requester));
            communityRepository.incrementMemberCount(communityId);
            communityChat.addMember(communityId, requester.getId());
        }
        log(communityId, staff.getId(), requester.getId(),
                approve ? ModerationAction.REQUEST_APPROVED : ModerationAction.REQUEST_REJECTED, null);
        eventPublisher.publishEvent(new JoinRequestDecided(community.getSlug(), community.getName(), staff.getId(),
                requester.getId(), approve));
    }

    @Transactional(readOnly = true)
    public CursorPageDto<JoinRequestDto> requests(UUID communityPublicId, UserAccount user, String before, int limit) {
        Long communityId = internalId(communityPublicId);
        requireStaff(communityId, user);
        return page(before, limit, OpaqueCursor.JOIN_REQUEST,
                cursor -> joinRequestRepository.findByPublicIdAndCommunityId(cursor.publicId(), communityId)
                        .map(r -> new Position(r.getCreatedAt(), r.getId())),
                fetch -> joinRequestRepository.findFirstPage(communityId, fetch),
                (position, fetch) -> joinRequestRepository.findPageBefore(communityId, position.at(), position.id(), fetch),
                JoinRequestDto::requestedAt, JoinRequestDto::id);
    }

    // ---- Expelled and log ----

    /** "Expulsadas": only for the staff; everybody else gets the 404 of a missing community. */
    @Transactional(readOnly = true)
    public CursorPageDto<BannedMemberDto> banned(UUID communityPublicId, UserAccount user, String before, int limit) {
        Long communityId = internalId(communityPublicId);
        requireStaff(communityId, user);
        return page(before, limit, OpaqueCursor.BANNED,
                cursor -> logRepository.findByPublicIdAndCommunityId(cursor.publicId(), communityId)
                        .map(l -> new Position(l.getCreatedAt(), l.getId())),
                fetch -> logRepository.findFirstBannedPage(communityId, fetch),
                (position, fetch) -> logRepository.findBannedPageBefore(communityId, position.at(), position.id(), fetch),
                BannedMemberDto::bannedAt, BannedMemberDto::id);
    }

    @Transactional(readOnly = true)
    public CursorPageDto<ModerationLogEntryDto> moderationLog(UUID communityPublicId, UserAccount user, String before, int limit) {
        Long communityId = internalId(communityPublicId);
        requireStaff(communityId, user);
        return page(before, limit, OpaqueCursor.MODERATION_LOG,
                cursor -> logRepository.findByPublicIdAndCommunityId(cursor.publicId(), communityId)
                        .map(l -> new Position(l.getCreatedAt(), l.getId())),
                fetch -> logRepository.findFirstPage(communityId, fetch),
                (position, fetch) -> logRepository.findPageBefore(communityId, position.at(), position.id(), fetch),
                ModerationLogEntryDto::createdAt, ModerationLogEntryDto::id);
    }

    private record Position(Instant at, Long id) {
    }

    /**
     * Keyset page: asks for one more row than the page to know whether there is a next one. The position of the
     * cursor comes from the row it names, which must belong to this community (400 otherwise), not from the token.
     */
    private <T> CursorPageDto<T> page(String before, int limit, String kind,
                                      Function<OpaqueCursor, java.util.Optional<Position>> positionOf,
                                      Function<Limit, List<T>> first,
                                      java.util.function.BiFunction<Position, Limit, List<T>> after,
                                      Function<T, Instant> createdAt, Function<T, UUID> publicId) {
        int size = Math.clamp(limit, 1, MAX_PAGE);
        OpaqueCursor cursor = OpaqueCursor.decode(before, kind);
        Limit fetch = Limit.of(size + 1);
        List<T> rows;
        if (cursor == null) {
            rows = first.apply(fetch);
        } else {
            Position position = positionOf.apply(cursor).orElseThrow(() -> new BadRequestException("Cursor no válido"));
            rows = after.apply(position, fetch);
        }
        boolean hasMore = rows.size() > size;
        List<T> items = rows.subList(0, Math.min(size, rows.size()));
        String next = null;
        if (hasMore) {
            T last = items.get(items.size() - 1);
            next = OpaqueCursor.encode(kind, LocalDateTime.ofInstant(createdAt.apply(last), ZoneOffset.UTC), publicId.apply(last));
        }
        return new CursorPageDto<>(items, next);
    }

    // ---- Shared rules ----

    private record Acting(CommunityMember actor, CommunityMember target) {
    }

    /**
     * The acting staff member and the target, both locked in a fixed order (so two moderators acting on each other do
     * not deadlock). Not staff: 404 like a missing community. Target not a member of the community: 404.
     */
    private Acting lockActingPair(Long communityId, UserAccount user, String username) {
        requireStaff(communityId, user);
        UserAccount targetUser = userAccountRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException(NO_MEMBER));
        boolean actorFirst = user.getId() < targetUser.getId();
        CommunityMember first = memberRepository.lockMember(communityId, actorFirst ? user.getId() : targetUser.getId()).orElse(null);
        CommunityMember second = user.getId().equals(targetUser.getId()) ? first
                : memberRepository.lockMember(communityId, actorFirst ? targetUser.getId() : user.getId()).orElse(null);
        CommunityMember actor = actorFirst ? first : second;
        CommunityMember target = actorFirst ? second : first;
        // Re-checked under the lock: she may have been demoted or expelled a moment ago
        if (actor == null || actor.getStatus() == MemberStatus.BANNED || actor.getRole() == CommunityRole.MEMBER) {
            throw new NotFoundException(NOT_FOUND);
        }
        if (target == null) {
            throw new NotFoundException(NO_MEMBER);
        }
        return new Acting(actor, target);
    }

    /** Hierarchy for mute and ban: nobody on herself or on the owner; a moderator only on plain members. */
    private void authorize(Long communityId, UserAccount user, CommunityMember target, String ownerMessage) {
        rejectSelfOrOwner(target, user, ownerMessage);
        CommunityMember actor = memberRepository.findByCommunityIdAndUserAccountId(communityId, user.getId())
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
        if (actor.getRole() != CommunityRole.OWNER && target.getRole() != CommunityRole.MEMBER) {
            throw new ForbiddenException("Solo la propietaria puede moderar a otras moderadoras");
        }
    }

    private static void rejectSelfOrOwner(CommunityMember target, UserAccount user, String ownerMessage) {
        if (target.getRole() == CommunityRole.OWNER) {
            throw new ConflictException(ownerMessage);
        }
        if (target.getUserAccount().getId().equals(user.getId())) {
            throw new BadRequestException("No puedes aplicarte esta acción a ti misma");
        }
    }

    private static void requireNotBanned(CommunityMember target) {
        if (target.getStatus() == MemberStatus.BANNED) {
            throw new ConflictException("La usuaria está expulsada de la comunidad");
        }
    }

    /** The caller's member row if she is the owner or a moderator and not banned; otherwise the uniform 404. */
    private CommunityMember requireStaff(Long communityId, UserAccount user) {
        return memberRepository.findByCommunityIdAndUserAccountId(communityId, user.getId())
                .filter(m -> m.getStatus() != MemberStatus.BANNED)
                .filter(m -> m.getRole() == CommunityRole.OWNER || m.getRole() == CommunityRole.MODERATOR)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    private void log(Long communityId, Long actorId, Long targetId, ModerationAction action, String reason) {
        logRepository.save(new CommunityModerationLog(communityId, actorId, targetId, action, reason));
    }

    private Long internalId(UUID publicId) {
        return communityRepository.findIdByPublicId(publicId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /** A PATCH may name the community by its slug or by its public id. */
    private Long resolve(String ref) {
        try {
            return internalId(UUID.fromString(ref));
        } catch (IllegalArgumentException notAUuid) {
            return communityRepository.findIdBySlug(ref).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        }
    }

    private static String cleanReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }

    private static String requiredText(JsonNode body, String field, int max) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) {
            throw new BadRequestException("El campo " + field + " no es válido");
        }
        if (node.asText().length() > max) {
            throw new BadRequestException("El campo " + field + " no puede superar los " + max + " caracteres");
        }
        return node.asText();
    }

    /** Text or explicit null (clears it); blank also clears it. */
    private static String optionalText(JsonNode body, String field, int max) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String text = requiredText(body, field, max);
        return text.isBlank() ? null : text.strip();
    }

    private static CommunityVisibility visibility(JsonNode node) {
        try {
            return CommunityVisibility.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("La visibilidad debe ser PUBLIC o PRIVATE");
        }
    }
}
