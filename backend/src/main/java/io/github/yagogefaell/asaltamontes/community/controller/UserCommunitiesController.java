package io.github.yagogefaell.asaltamontes.community.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.community.dto.CommunitySearchDto;
import io.github.yagogefaell.asaltamontes.community.service.UserCommunitiesService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/** Communities of another user (T31b, D-023). */
@RestController
@RequestMapping("/api/users/{username}/communities")
@RequiredArgsConstructor
public class UserCommunitiesController {

    private final UserCommunitiesService communities;

    /** Only her PUBLIC communities; the role in each one is the caller's own, as in every community listing. */
    @GetMapping
    public ResponseEntity<Page<CommunitySearchDto>> getUserCommunities(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(communities.publicCommunitiesOf(username, currentUser, pageable));
    }
}
