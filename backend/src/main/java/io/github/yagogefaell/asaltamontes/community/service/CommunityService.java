package io.github.yagogefaell.asaltamontes.community.service;

import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.SortWhitelist;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.domain.MemberStatus;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
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

    public CommunityService(CommunityRepository communityRepository, CommunityMemberRepository communityMemberRepository) {
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
    }
    
    @Transactional
    public Community create(CreateCommunityDto request, UserAccount currentUser) {
        String name = SlugGenerator.normalizeName(request.name());
        if (communityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Ya existe una comunidad con ese nombre");
        }

        Community community = new Community(
            name,
            uniqueSlug(name),
            request.description(),
            request.imageUrl(),
            currentUser
        );

        community = communityRepository.save(community);

        CommunityMember communityMember = new CommunityMember(community, currentUser, CommunityRole.OWNER);
        communityMemberRepository.save(communityMember);
        
        return community;
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
    public Page<CommunitySearchDto> getAllCommunities(UserAccount currentUser, String query, Pageable pageable) {
        Pageable sorted = COMMUNITY_SORT.apply(pageable);
        if (query == null || query.isBlank()) {
            return communityRepository.findAllCommunities(currentUser.getId(), sorted);
        }
        return communityRepository.searchCommunities(currentUser.getId(), likePattern(query), sorted);
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

    public Page<CommunityMemberDto> getCommunityMembersExcept(Long communityId, Long userId, Pageable pageable) {
        return communityRepository.findMembersExcept(communityId, userId, MEMBER_SORT.apply(pageable));
    }

    /** Whether the user created the community; 404 if it does not exist (BUG-014). */
    public boolean isOwner(Long communityId, UserAccount currentUser) {
        requireExists(communityId);
        return communityRepository.existsByIdAndCreatedById(communityId, currentUser.getId());
    }

    @Transactional
    public void joinCommunity(Long communityId, UserAccount currentUser) {
        requireExists(communityId);
        communityMemberRepository.findStatus(communityId, currentUser.getId()).ifPresent(status -> {
            if (status == MemberStatus.BANNED) {
                throw new ForbiddenException("No puedes unirte a esta comunidad");
            }
            throw new ConflictException("Ya eres miembro de esta comunidad");
        });

        Community communityRef = communityRepository.getReferenceById(communityId);
        CommunityMember communityMember = new CommunityMember(communityRef, currentUser);
        communityMemberRepository.saveAndFlush(communityMember);
        communityRepository.incrementMemberCount(communityId);
    }

    @Transactional
    public void leaveCommunity(Long communityId, UserAccount currentUser) {
        long rowsDeleted = communityMemberRepository.removeMember(communityId, currentUser.getId());

        if (rowsDeleted == 0) {
            throw new NotFoundException("No eres miembro de esta comunidad");
        }
        communityRepository.decrementMemberCount(communityId);
    }

    @Transactional
    public void deleteCommunity(Long communityId, UserAccount currentUser) {
        if (!isOwner(communityId, currentUser)) {
            throw new ForbiddenException("Solo la propietaria puede borrar la comunidad");
        }
        communityMemberRepository.deleteAllByCommunityId(communityId);
        communityRepository.deleteById(communityId);
    }

    private void requireExists(Long communityId) {
        if (!communityRepository.existsById(communityId)) {
            throw new NotFoundException("Comunidad no encontrada");
        }
    }
}
