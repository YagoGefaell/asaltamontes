package io.github.yagogefaell.asaltamontes.community.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityMemberRepository;
import io.github.yagogefaell.asaltamontes.community.repository.CommunityRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@Service
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;

    public CommunityService(CommunityRepository communityRepository, CommunityMemberRepository communityMemberRepository) {
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
    }
    
    @Transactional
    public Community create(CreateCommunityDto request, UserAccount currentUser) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Community name cannot be empty");
        }
        else if (request.name().length() > 100) {
            throw new IllegalArgumentException("Community name cannot be longer than 100 characters");
        }
        else if (communityRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Community with that name already exists");
        }

        if (request.description() != null && request.description().length() > 500) {
            throw new IllegalArgumentException("Community description cannot be longer than 500 characters");
        }

        Community community = new Community(
            request.name(),
            request.description(),
            request.imageUrl(),
            currentUser
        );

        communityRepository.save(community);
        return community;
    }

    public Page<CommunityDto> getMyCommunities(Long currentUserId, Pageable pageable) {
        return communityMemberRepository.findCommunitiesByUserId(currentUserId, pageable);
    }

    public Page<CommunityDto> getAllCommunities(Pageable pageable) {
        return communityRepository.findAllCommunities(pageable);
    }

    public CommunityDto getCommunityById(Long communityId) {
        return communityRepository.findCommunityById(communityId)
            .orElseThrow(() -> new IllegalArgumentException("Community not found"));
    }

    public Page<CommunityMemberDto> getCommunityMembersExcept(Long communityId, Long userId, Pageable pageable) {
        return communityRepository.findMembersExcept(communityId, userId, pageable);
    }

    @Transactional
    public void joinCommunity(Long communityId, UserAccount currentUser) {
        Community communityRef = communityRepository.getReferenceById(communityId);

        CommunityMember communityMember = new CommunityMember(communityRef, currentUser);
        communityMemberRepository.saveAndFlush(communityMember);
    }

    @Transactional
    public void leaveCommunity(Long communityId, Long currentUserId) {
        long rowsDeleted = communityMemberRepository.removeMember(communityId, currentUserId);

        if (rowsDeleted == 0) {
            throw new IllegalArgumentException("User is not a member of this community");
        }
    }
}

