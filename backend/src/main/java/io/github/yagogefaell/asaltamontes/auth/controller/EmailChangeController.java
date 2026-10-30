package io.github.yagogefaell.asaltamontes.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.auth.dto.ChangeEmailRequest;
import io.github.yagogefaell.asaltamontes.auth.service.EmailChangeService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/email-change")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    /** Asks for the change. 202: nothing changes until the link sent to the new address is used. */
    @PostMapping
    public ResponseEntity<Void> request(@AuthenticationPrincipal UserAccount currentUser,
                                        @Valid @RequestBody ChangeEmailRequest request) {
        emailChangeService.request(currentUser, request.newEmail(), request.currentPassword());
        return ResponseEntity.accepted().build();
    }
}
