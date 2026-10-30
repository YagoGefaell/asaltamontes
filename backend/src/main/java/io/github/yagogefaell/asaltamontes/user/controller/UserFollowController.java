package io.github.yagogefaell.asaltamontes.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.dto.FollowRequestDto;
import io.github.yagogefaell.asaltamontes.user.dto.FollowStatusDto;
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;
import io.github.yagogefaell.asaltamontes.user.service.FollowService;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserFollowController {

    private final FollowService followService;

    @PostMapping("/follow/{username}")
    public ResponseEntity<FollowStatusDto> followUser(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username
    ) {
        // 202: a private profile got a request that its owner has to approve (T23a)
        FollowService.FollowOutcome outcome = followService.followUser(currentUser.getId(), username);
        return outcome == FollowService.FollowOutcome.REQUESTED
                ? ResponseEntity.accepted().body(new FollowStatusDto("REQUESTED"))
                : ResponseEntity.ok(new FollowStatusDto("FOLLOWING"));
    }

    @DeleteMapping("/follow/{username}")
    public ResponseEntity<Void> unfollowUser(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username
    ) {
        followService.unfollowUser(currentUser.getId(), username);
        return ResponseEntity.noContent().build();
    }

    /** Requests to follow my private profile, newest first. */
    @GetMapping("/me/follow-requests")
    public Page<FollowRequestDto> getFollowRequests(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return followService.getRequests(currentUser.getId(),
                PageRequest.of(pageable.getPageNumber(), Math.clamp(pageable.getPageSize(), 1, 50)));
    }

    @PostMapping("/me/follow-requests/{username}/approve")
    public ResponseEntity<Void> approveFollowRequest(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username
    ) {
        followService.approveRequest(currentUser.getId(), username);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/follow-requests/{username}/reject")
    public ResponseEntity<Void> rejectFollowRequest(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username
    ) {
        followService.rejectRequest(currentUser.getId(), username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/following")
    public Page<UserSearchDto> getFollowing(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return followService.getUsersFollowedBy(currentUser.getUsername(), currentUser.getId(), pageable);
    }

    @GetMapping("/following/{username}")
    public Page<UserSearchDto> getFollowingByUsername(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return followService.getUsersFollowedBy(username, currentUser.getId(), pageable);
    }

    @GetMapping("/followers")
    public Page<UserSearchDto> getFollowers(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return followService.getUsersFollowing(currentUser.getUsername(), currentUser.getId(), pageable);
    }

    @GetMapping("/followers/{username}")
    public Page<UserSearchDto> getFollowersByUsername(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return followService.getUsersFollowing(username, currentUser.getId(), pageable);
    }
}

