package io.github.yagogefaell.asaltamontes.community.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityMember;
import io.github.yagogefaell.asaltamontes.community.domain.CommunityRole;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;
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
        if (communityRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Community with that name already exists");
        }

        Community community = new Community(
            request.name(),
            request.description(),
            request.imageUrl(),
            currentUser
        );

        community = communityRepository.save(community);

        CommunityMember communityMember = new CommunityMember(community, currentUser, CommunityRole.OWNER);
        communityMemberRepository.save(communityMember);
        
        return community;
    }

    public Page<CommunitySearchDto> getMyCommunities(UserAccount currentUser, Pageable pageable) {
        return communityMemberRepository.findCommunitiesByUserId(currentUser.getId(), pageable);
    }

    public Page<CommunitySearchDto> getAllCommunities(UserAccount currentUser, Pageable pageable) {
        return communityRepository.findAllCommunities(currentUser.getId(), pageable);
    }

    public CommunityDetailsDto getCommunityByName(String communityName, UserAccount currentUser) {
        return communityRepository.findCommunityByName(communityName, currentUser.getId())
            .orElseThrow(() -> new IllegalArgumentException("Community not found"));
    }

    public Page<CommunityMemberDto> getCommunityMembersExcept(Long communityId, Long userId, Pageable pageable) {
        return communityRepository.findMembersExcept(communityId, userId, pageable);
    }

    public boolean isOwner(Long communityId, UserAccount currentUser) {
        return communityRepository.isOwner(communityId, currentUser.getId());
    }

    @Transactional
    public void joinCommunity(Long communityId, UserAccount currentUser) {
        Community communityRef = communityRepository.getReferenceById(communityId);

        CommunityMember communityMember = new CommunityMember(communityRef, currentUser);
        communityMemberRepository.saveAndFlush(communityMember);
        communityRepository.incrementMemberCount(communityId);
    }

    @Transactional
    public void leaveCommunity(Long communityId, UserAccount currentUser) {
        long rowsDeleted = communityMemberRepository.removeMember(communityId, currentUser.getId());

        if (rowsDeleted == 0) {
            throw new IllegalArgumentException("User is not a member of this community");
        }
        communityRepository.decrementMemberCount(communityId);
    }
}

