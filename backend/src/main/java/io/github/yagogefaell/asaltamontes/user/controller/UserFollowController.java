package io.github.yagogefaell.asaltamontes.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
            Authentication authentication,
            @PathVariable String username
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        followService.followUser(user.getId(), username);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/follow/{username}")
    public ResponseEntity<Void> unfollowUser(
            Authentication authentication,
            @PathVariable String username
    ) {
        UserAccount user = (UserAccount) authentication.getPrincipal();
        followService.unfollowUser(user.getId(), username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/following")
    public Page<UserSearchDto> getFollowing(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "following.username") Pageable pageable
    ) {
        Long followerId = ((UserAccount) authentication.getPrincipal()).getId();
        return followService.getUsersFollowing(followerId, pageable);
    }

    @GetMapping("/followers")
    public Page<UserSearchDto> getFollowers(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "follower.username") Pageable pageable
    ) {
        Long followingId = ((UserAccount) authentication.getPrincipal()).getId();
        return followService.getUsersFollowedBy(followingId, pageable);
    }
}

