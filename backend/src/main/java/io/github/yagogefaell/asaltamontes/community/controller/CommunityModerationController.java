package io.github.yagogefaell.asaltamontes.community.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.community.dto.BannedMemberDto;
import io.github.yagogefaell.asaltamontes.community.dto.ChangeRoleDto;
import io.github.yagogefaell.asaltamontes.community.dto.CursorPageDto;
import io.github.yagogefaell.asaltamontes.community.dto.JoinRequestDto;
import io.github.yagogefaell.asaltamontes.community.dto.ModerationLogEntryDto;
import io.github.yagogefaell.asaltamontes.community.dto.ModerationReasonDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityModerationService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Moderation of a community (T19). Everything here is for the owner and the moderators; others get 404. */
@RestController
@RequestMapping("/api/communities/{communityId}")
@RequiredArgsConstructor
public class CommunityModerationController {

    private final CommunityModerationService moderationService;

    @PutMapping("/members/{username}/role")
    public ResponseEntity<Void> changeRole(@PathVariable UUID communityId, @PathVariable String username,
                                           @AuthenticationPrincipal UserAccount currentUser,
                                           @Valid @RequestBody ChangeRoleDto request) {
        moderationService.changeRole(communityId, username, request.role(), currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{username}/mute")
    public ResponseEntity<Void> mute(@PathVariable UUID communityId, @PathVariable String username,
                                     @AuthenticationPrincipal UserAccount currentUser,
                                     @Valid @RequestBody(required = false) ModerationReasonDto request) {
        moderationService.mute(communityId, username, request == null ? null : request.reason(), currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{username}/unmute")
    public ResponseEntity<Void> unmute(@PathVariable UUID communityId, @PathVariable String username,
                                       @AuthenticationPrincipal UserAccount currentUser) {
        moderationService.unmute(communityId, username, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{username}/ban")
    public ResponseEntity<Void> ban(@PathVariable UUID communityId, @PathVariable String username,
                                    @AuthenticationPrincipal UserAccount currentUser,
                                    @Valid @RequestBody(required = false) ModerationReasonDto request) {
        moderationService.ban(communityId, username, request == null ? null : request.reason(), currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/members/{username}/unban")
    public ResponseEntity<Void> unban(@PathVariable UUID communityId, @PathVariable String username,
                                      @AuthenticationPrincipal UserAccount currentUser) {
        moderationService.unban(communityId, username, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/transfer-ownership/{username}")
    public ResponseEntity<Void> transferOwnership(@PathVariable UUID communityId, @PathVariable String username,
                                                  @AuthenticationPrincipal UserAccount currentUser) {
        moderationService.transferOwnership(communityId, username, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/banned")
    public ResponseEntity<CursorPageDto<BannedMemberDto>> banned(@PathVariable UUID communityId,
                                                                 @AuthenticationPrincipal UserAccount currentUser,
                                                                 @RequestParam(required = false) String before,
                                                                 @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(moderationService.banned(communityId, currentUser, before, limit));
    }

    @GetMapping("/requests")
    public ResponseEntity<CursorPageDto<JoinRequestDto>> requests(@PathVariable UUID communityId,
                                                                  @AuthenticationPrincipal UserAccount currentUser,
                                                                  @RequestParam(required = false) String before,
                                                                  @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(moderationService.requests(communityId, currentUser, before, limit));
    }

    @PostMapping("/requests/{username}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID communityId, @PathVariable String username,
                                        @AuthenticationPrincipal UserAccount currentUser) {
        moderationService.approveRequest(communityId, username, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{username}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID communityId, @PathVariable String username,
                                       @AuthenticationPrincipal UserAccount currentUser) {
        moderationService.rejectRequest(communityId, username, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/moderation-log")
    public ResponseEntity<CursorPageDto<ModerationLogEntryDto>> moderationLog(@PathVariable UUID communityId,
                                                                              @AuthenticationPrincipal UserAccount currentUser,
                                                                              @RequestParam(required = false) String before,
                                                                              @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(moderationService.moderationLog(communityId, currentUser, before, limit));
    }
}
