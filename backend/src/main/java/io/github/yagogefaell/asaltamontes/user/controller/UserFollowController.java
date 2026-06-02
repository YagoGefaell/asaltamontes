package io.github.yagogefaell.asaltamontes.user.controller;

import org.springframework.data.domain.Page;
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
import io.github.yagogefaell.asaltamontes.user.dto.UserSearchDto;
import io.github.yagogefaell.asaltamontes.user.service.FollowService;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserFollowController {

    private final FollowService followService;

    @PostMapping("/follow/{username}")
    public ResponseEntity<Void> followUser(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username
    ) {
        followService.followUser(currentUser.getId(), username);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/follow/{username}")
    public ResponseEntity<Void> unfollowUser(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username
    ) {
        followService.unfollowUser(currentUser.getId(), username);
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

