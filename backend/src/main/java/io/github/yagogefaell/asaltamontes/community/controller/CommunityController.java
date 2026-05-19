package io.github.yagogefaell.asaltamontes.community.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping
    public ResponseEntity<?> createCommunity(
            @AuthenticationPrincipal UserAccount currentUser,
            @Valid @RequestBody CreateCommunityDto request) {

        communityService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<Page<CommunitySearchDto>> getAllCommunities(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
        ) {
        Page<CommunitySearchDto> communities = communityService.getAllCommunities(currentUser, pageable);
        return ResponseEntity.ok(communities);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<CommunitySearchDto>> getMyCommunities(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<CommunitySearchDto> communities = communityService.getMyCommunities(currentUser, pageable);
        return ResponseEntity.ok(communities);
    }

    @GetMapping("/{communityName}")
    public ResponseEntity<CommunityDetailsDto> getCommunity(@PathVariable String communityName, @AuthenticationPrincipal UserAccount currentUser) {
        CommunityDetailsDto community = communityService.getCommunityByName(communityName, currentUser);
        return ResponseEntity.ok(community);
    }

    @PostMapping("/{communityId}/join")
    public ResponseEntity<Void> joinCommunity(
            @PathVariable Long communityId,
            @AuthenticationPrincipal UserAccount currentUser) {
        communityService.joinCommunity(communityId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{communityId}/leave")
    public ResponseEntity<Void> leaveCommunity(
            @PathVariable Long communityId,
            @AuthenticationPrincipal UserAccount currentUser) {
        communityService.leaveCommunity(communityId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{communityId}/members")
    public ResponseEntity<Page<CommunityMemberDto>> getCommunityMembers(
            @PathVariable Long communityId,
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<CommunityMemberDto> members = communityService.getCommunityMembersExcept(communityId, currentUser.getId(), pageable);
        return ResponseEntity.ok(members);
    }
}