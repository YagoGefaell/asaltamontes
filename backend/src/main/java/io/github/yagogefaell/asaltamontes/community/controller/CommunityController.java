package io.github.yagogefaell.asaltamontes.community.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.community.domain.Community;
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
    public ResponseEntity<CommunityDetailsDto> createCommunity(
            @AuthenticationPrincipal UserAccount currentUser,
            @Valid @RequestBody CreateCommunityDto request) {

        Community community = communityService.create(request, currentUser);
        CommunityDetailsDto created = communityService.getCommunity(community.getSlug(), currentUser);
        return ResponseEntity.created(URI.create("/api/communities/" + created.slug())).body(created);
    }

    @GetMapping
    public ResponseEntity<Page<CommunitySearchDto>> getAllCommunities(
            @AuthenticationPrincipal UserAccount currentUser,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable
        ) {
        Page<CommunitySearchDto> communities = communityService.getAllCommunities(currentUser, query, pageable);
        return ResponseEntity.ok(communities);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<CommunitySearchDto>> getMyCommunities(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CommunitySearchDto> communities = communityService.getMyCommunities(currentUser, pageable);
        return ResponseEntity.ok(communities);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CommunityDetailsDto> getCommunity(@PathVariable String slug, @AuthenticationPrincipal UserAccount currentUser) {
        CommunityDetailsDto community = communityService.getCommunity(slug, currentUser);
        return ResponseEntity.ok(community);
    }

    @PostMapping("/{communityId}/join")
    public ResponseEntity<Void> joinCommunity(
            @PathVariable UUID communityId,
            @AuthenticationPrincipal UserAccount currentUser) {
        communityService.joinCommunity(communityId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{communityId}/leave")
    public ResponseEntity<Void> leaveCommunity(
            @PathVariable UUID communityId,
            @AuthenticationPrincipal UserAccount currentUser) {
        if (communityService.isOwner(communityId, currentUser)) {
            throw new ConflictException("La propietaria no puede salir de la comunidad");
        }
        communityService.leaveCommunity(communityId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{communityId}/members")
    public ResponseEntity<Page<CommunityMemberDto>> getCommunityMembers(
            @PathVariable UUID communityId,
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CommunityMemberDto> members = communityService.getCommunityMembersExcept(communityId, currentUser.getId(), pageable);
        return ResponseEntity.ok(members);
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> deleteCommunity(
            @PathVariable UUID communityId,
            @AuthenticationPrincipal UserAccount currentUser) {
        communityService.deleteCommunity(communityId, currentUser);
        return ResponseEntity.noContent().build();
    }
}