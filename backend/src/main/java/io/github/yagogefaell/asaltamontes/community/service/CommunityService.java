package io.github.yagogefaell.asaltamontes.community.service;

import io.github.yagogefaell.asaltamontes.media.domain.MediaPurpose;
import io.github.yagogefaell.asaltamontes.media.service.MediaReferenceService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityModerationLogRepository;
import io.github.yagogefaell.asaltamontes.common.pagination.SortWhitelist;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityJoinRequestRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.common.identifier.UuidV7;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.message.service.CommunityChatService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@Service
public class CommunityService {

    // Allowed ?sort= of each listing, with the alias of its query (T14, BUG-012)
    private static final SortWhitelist COMMUNITY_SORT = new SortWhitelist(
            Map.of("name", "c.name", "memberCount", "c.memberCount", "createdAt", "c.createdAt"), "name");
    private static final SortWhitelist MEMBER_SORT = new SortWhitelist(
            Map.of("username", "u.username", "joinedAt", "cm.joinedAt"), "username");

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final MediaReferenceService mediaReferences;
    private final CommunityJoinRequestRepository joinRequestRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CommunityModerationLogRepository moderationLogRepository;
    private final Duration joinRequestCooldown;
    private final CommunityChatService communityChat;

    /** What joining did: she is a member now, or her request waits for the moderation (private communities). */
    public enum JoinResult { JOINED, REQUESTED }

    public CommunityService(CommunityRepository communityRepository, CommunityMemberRepository communityMemberRepository,
                            MediaReferenceService mediaReferences, CommunityJoinRequestRepository joinRequestRepository,
                            ApplicationEventPublisher eventPublisher,
                            CommunityModerationLogRepository moderationLogRepository,
                            CommunityChatService communityChat,
                            @Value("${asaltamontes.community.join-request-cooldown:7d}") Duration joinRequestCooldown) {
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
        this.mediaReferences = mediaReferences;
        this.joinRequestRepository = joinRequestRepository;
        this.eventPublisher = eventPublisher;
        this.moderationLogRepository = moderationLogRepository;
        this.communityChat = communityChat;
        this.joinRequestCooldown = joinRequestCooldown;
    }
    
    @Transactional
    public Community create(CreateCommunityDto request, UserAccount currentUser) {
        String name = SlugGenerator.normalizeName(request.name());
        if (communityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ya existe una comunidad con ese nombre");
        }

        // The creator becomes OWNER, so she may set the cover; the image must be hers
        MediaReferenceService.Linked cover = mediaReferences.link(request.coverMediaId(), request.imageUrl(),
                MediaPurpose.COMMUNITY_COVER, currentUser.getId(), null);

        Community community = new Community(
            name,
            uniqueSlug(name),
            request.description(),
            cover.url(),
            currentUser
        );
        community.setCoverMediaId(cover.mediaId());
        if (request.visibility() != null) {
            community.setVisibility(request.visibility());
        }
        community.setCity(blankToNull(request.city()));
        community.setProvince(blankToNull(request.province()));

        community = communityRepository.save(community);

        CommunityMember communityMember = new CommunityMember(community, currentUser, CommunityRole.OWNER);
        communityMemberRepository.saveAndFlush(communityMember);
        communityChat.ensureChat(community.getId());

        return community;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** "trail-vigo", or "trail-vigo-2", "-3"… if taken (the unique constraint is the last word on races). */
    private String uniqueSlug(String name) {
        String base = SlugGenerator.slugify(name);
        String slug = base;
        for (int suffix = 2; communityRepository.existsBySlug(slug); suffix++) {
            slug = base + "-" + suffix;
        }
        return slug;
    }

    public Page<CommunitySearchDto> getMyCommunities(UserAccount currentUser, Pageable pageable) {
        return communityMemberRepository.findCommunitiesByUserId(currentUser.getId(), COMMUNITY_SORT.apply(pageable));
    }

    /** All communities, or those whose name contains {@code query} (case-insensitive). */
    public Page<CommunitySearchDto> getAllCommunities(UserAccount currentUser, String query, String city, String province,
                                                      Pageable pageable) {
        String pattern = query == null || query.isBlank() ? "%" : likePattern(query);
        return communityRepository.searchCommunities(currentUser.getId(), pattern, lowerOrEmpty(city), lowerOrEmpty(province),
                COMMUNITY_SORT.apply(pageable));
    }

    // "" means "any" (a null parameter would have no type for PostgreSQL)
    private static String lowerOrEmpty(String value) {
        return value == null ? "" : SlugGenerator.normalizeName(value).toLowerCase(Locale.ROOT);
    }

    // %, _ and \ typed by the user are searched literally
    private static String likePattern(String query) {
        String escaped = SlugGenerator.normalizeName(query).toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** By slug; links shared before T14 used the name, which still works. */
    public CommunityDetailsDto getCommunity(String slugOrName, UserAccount currentUser) {
        return communityRepository.findCommunityBySlug(slugOrName, currentUser.getId())
            .or(() -> communityRepository.findCommunityByName(SlugGenerator.normalizeName(slugOrName), currentUser.getId()))
            .orElseThrow(() -> new NotFoundException("Comunidad no encontrada"));
    }

    /**
     * Members of the community other than the caller. Only members who are not banned may see them (BUG-066):
     * anyone else gets the same 404 as for a community that does not exist, so it does not reveal which ones exist.
     */
    public Page<CommunityMemberDto> getCommunityMembersExcept(UUID communityPublicId, UserAccount currentUser, Pageable pageable) {
        Long communityId = communityRepository.findIdByPublicId(communityPublicId)
                .filter(id -> communityMemberRepository.findStatus(id, currentUser.getId())
                        .filter(status -> status != MemberStatus.BANNED).isPresent())
                .orElseThrow(() -> new NotFoundException("Comunidad no encontrada"));
        Page<CommunityMemberDto> members = communityRepository.findMembersExcept(communityId, currentUser.getId(), MEMBER_SORT.apply(pageable));
        // Banned members are not listed; the muted mark is only for the moderation (BUG-067)
        return communityMemberRepository.isModerator(communityId, currentUser.getId())
                ? members
                : members.map(CommunityMemberDto::withoutStatus);
    }

    /** Whether the user created the community; 404 if it does not exist (BUG-014). */
    public boolean isOwner(UUID communityPublicId, UserAccount currentUser) {
        return isOwner(internalId(communityPublicId), currentUser);
    }

    private boolean isOwner(Long communityId, UserAccount currentUser) {
        // By role, not by creator: the ownership can be transferred (T19)
        return communityMemberRepository.isOwner(communityId, currentUser.getId());
    }

    @Transactional
    public JoinResult joinCommunity(UUID communityPublicId, UserAccount currentUser) {
        Long communityId = internalId(communityPublicId);
        communityMemberRepository.findStatus(communityId, currentUser.getId()).ifPresent(status -> {
            if (status == MemberStatus.BANNED) {
                // The message is neutral, but the 403 (vs 201/409) does tell her she is banned; accepted by Yago (BUG-067)
                throw new ForbiddenException("No puedes unirte a esta comunidad");
            }
            throw new ConflictException("Ya eres miembro de esta comunidad");
        });

        Community community = communityRepository.getReferenceById(communityId);
        if (community.getVisibility() == CommunityVisibility.PRIVATE) {
            requestToJoin(community, currentUser);
            return JoinResult.REQUESTED;
        }
        CommunityMember communityMember = new CommunityMember(community, currentUser);
        communityMemberRepository.saveAndFlush(communityMember);
        communityRepository.incrementMemberCount(communityId);
        communityChat.addMember(communityId, currentUser.getId());
        return JoinResult.JOINED;
    }

    /** Idempotent: asking twice leaves one request and notifies the staff once. */
    private void requestToJoin(Community community, UserAccount requester) {
        requireRejectionCooldownOver(community.getId(), requester.getId());
        int inserted = joinRequestRepository.insertIfAbsent(UuidV7.next(), community.getId(), requester.getId(), Instant.now());
        if (inserted > 0) {
            eventPublisher.publishEvent(new JoinRequested(community.getSlug(), community.getName(), requester.getId(),
                    communityMemberRepository.findStaffUserIds(community.getId())));
        }
    }

    /** After a rejection she must wait (T19, {@code asaltamontes.community.join-request-cooldown}): 429 with Retry-After. */
    private void requireRejectionCooldownOver(Long communityId, Long userId) {
        if (joinRequestCooldown.isZero() || joinRequestCooldown.isNegative()) {
            return;
        }
        moderationLogRepository.findLastRejection(communityId, userId).ifPresent(rejectedAt -> {
            Instant now = Instant.now();
            Instant until = rejectedAt.plus(joinRequestCooldown);
            if (until.isAfter(now)) {
                long seconds = Math.max(1, Duration.between(now, until).toSeconds() + 1);
                long days = (seconds + 86_399) / 86_400;
                throw new TooManyRequestsException("Tu solicitud fue rechazada. Podrás volver a solicitarlo en "
                        + days + (days == 1 ? " día" : " días"), seconds);
            }
        });
    }

    @Transactional
    public void leaveCommunity(UUID communityPublicId, UserAccount currentUser) {
        Long communityId = internalId(communityPublicId);
        // Otherwise leaving and joining again would lift the mute
        if (communityMemberRepository.findStatus(communityId, currentUser.getId()).filter(s -> s == MemberStatus.MUTED).isPresent()) {
            throw new ConflictException("No puedes salir de la comunidad mientras estés silenciada");
        }
        long rowsDeleted = communityMemberRepository.removeMember(communityId, currentUser.getId());

        if (rowsDeleted == 0) {
            // Not a member: leaving withdraws a pending request, if she has one
            if (joinRequestRepository.deleteRequest(communityId, currentUser.getId()) > 0) {
                return;
            }
            throw new NotFoundException("No eres miembro de esta comunidad");
        }
        communityRepository.decrementMemberCount(communityId);
        communityChat.removeMember(communityId, currentUser.getId(), "LEFT");
    }

    @Transactional
    public void deleteCommunity(UUID communityPublicId, UserAccount currentUser) {
        Long communityId = internalId(communityPublicId);
        if (!isOwner(communityId, currentUser)) {
            throw new ForbiddenException("Solo la propietaria puede borrar la comunidad");
        }
        communityChat.deleteChat(communityId);
        communityMemberRepository.deleteAllByCommunityId(communityId);
        communityRepository.deleteById(communityId);
    }

    /** Internal id of a community given by its public id (D-021); 404 if it does not exist. */
    private Long internalId(UUID communityPublicId) {
        return communityRepository.findIdByPublicId(communityPublicId)
                .orElseThrow(() -> new NotFoundException("Comunidad no encontrada"));
    }
}
