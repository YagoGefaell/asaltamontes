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
import io.github.yagogefaell.asaltamontes.user.dto.BlockedUserDto;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserBlockController {

    private final BlockService blockService;

    /** Idempotent: blocking someone already blocked is a 204 as well. */
    @PostMapping("/{username}/block")
    public ResponseEntity<Void> block(@AuthenticationPrincipal UserAccount currentUser, @PathVariable String username) {
        blockService.block(currentUser.getId(), username);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{username}/block")
    public ResponseEntity<Void> unblock(@AuthenticationPrincipal UserAccount currentUser, @PathVariable String username) {
        blockService.unblock(currentUser.getId(), username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/blocks")
    public Page<BlockedUserDto> getBlocked(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return blockService.getBlocked(currentUser.getId(), pageable);
    }
}
