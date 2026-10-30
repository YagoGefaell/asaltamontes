package io.github.yagogefaell.asaltamontes.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.admin.service.AdminReportService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/** Account actions of the moderation panel (T23c2). Staff only. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminReportService service;

    @PostMapping("/{username}/unsuspend")
    public ResponseEntity<Void> unsuspend(@AuthenticationPrincipal UserAccount actor, @PathVariable String username) {
        service.unsuspend(actor, username);
        return ResponseEntity.noContent().build();
    }
}
