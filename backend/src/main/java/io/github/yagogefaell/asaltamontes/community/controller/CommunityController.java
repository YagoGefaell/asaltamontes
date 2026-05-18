package io.github.yagogefaell.asaltamontes.community.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Community> createCommunity(
            @AuthenticationPrincipal UserAccount currentUser,
            @RequestBody CreateCommunityDto request) {

        Community community = communityService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(community);
    }

    @GetMapping
    public ResponseEntity<Page<CommunityDto>> getAllCommunities(
            @PageableDefault(size = 20, sort = "name") Pageable pageable
        ) {
        Page<CommunityDto> communities = communityService.getAllCommunities(pageable);
        return ResponseEntity.ok(communities);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<CommunityDto>> getMyCommunities(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<CommunityDto> communities = communityService.getMyCommunities(currentUser.getId(), pageable);
        return ResponseEntity.ok(communities);
    }

    @GetMapping("/{communityId}")
    public ResponseEntity<CommunityDto> getCommunity(@PathVariable Long communityId) {
        CommunityDto community = communityService.getCommunityById(communityId);
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