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
import io.github.yagogefaell.asaltamontes.community.dto.CommunityChatDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityDetailsDto;
import io.github.yagogefaell.asaltamontes.message.service.CommunityChatService;
import io.github.yagogefaell.asaltamontes.community.dto.CommunityMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;
import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityModerationService;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.community.service.MyCommunitiesService;
import io.github.yagogefaell.asaltamontes.community.dto.MyCommunityDto;
import io.github.yagogefaell.asaltamontes.community.dto.PendingCommunitiesDto;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@RestController
@RequestMapping("/api/communities")
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityModerationService moderationService;
    private final CommunityChatService communityChat;
    private final MyCommunitiesService myCommunities;

    public CommunityController(CommunityService communityService, CommunityModerationService moderationService,
                               CommunityChatService communityChat, MyCommunitiesService myCommunities) {
        this.myCommunities = myCommunities;
        this.communityService = communityService;
        this.moderationService = moderationService;
        this.communityChat = communityChat;
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
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String province,
            @PageableDefault(size = 20) Pageable pageable
        ) {
        Page<CommunitySearchDto> communities = communityService.getAllCommunities(currentUser, query, city, province, pageable);
        return ResponseEntity.ok(communities);
    }

    @GetMapping("/me")
    public ResponseEntity<Page<MyCommunityDto>> getMyCommunities(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(myCommunities.myCommunities(currentUser, pageable));
    }

    /** Join requests waiting for her approval (as owner/moderator) and her own undecided ones (T31c). */
    @GetMapping("/me/pending")
    public ResponseEntity<PendingCommunitiesDto> getPending(@AuthenticationPrincipal UserAccount currentUser) {
        return ResponseEntity.ok(myCommunities.pending(currentUser));
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
        // 201: she is a member; 202: a private community, her request waits for the moderation
        CommunityService.JoinResult result = communityService.joinCommunity(communityId, currentUser);
        return ResponseEntity.status(result == CommunityService.JoinResult.JOINED ? HttpStatus.CREATED : HttpStatus.ACCEPTED).build();
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
        Page<CommunityMemberDto> members = communityService.getCommunityMembersExcept(communityId, currentUser, pageable);
        return ResponseEntity.ok(members);
    }

    /** The community's chat for a member (created on first access); anyone else gets the 404 of a missing community. */
    @GetMapping("/{communityId}/chat")
    public ResponseEntity<CommunityChatDto> getChat(
            @PathVariable UUID communityId,
            @AuthenticationPrincipal UserAccount currentUser) {
        return ResponseEntity.ok(communityChat.open(communityId, currentUser));
    }

    /** By slug or by public id; absent field = unchanged, explicit null = clears (D-020). */
    @PatchMapping("/{ref}")
    public ResponseEntity<CommunityDetailsDto> updateCommunity(
            @PathVariable String ref,
            @AuthenticationPrincipal UserAccount currentUser,
            @RequestBody JsonNode body) {
        return ResponseEntity.ok(moderationService.update(ref, body, currentUser));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<Void> deleteCommunity(
            @PathVariable UUID communityId,
            @AuthenticationPrincipal UserAccount currentUser) {
        communityService.deleteCommunity(communityId, currentUser);
        return ResponseEntity.noContent().build();
    }
}