package io.github.yagogefaell.asaltamontes.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.auth.service.EmailVerificationService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/verification-email")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    /** "Resend" of the banner. 202: the email is sent in the background. */
    @PostMapping
    public ResponseEntity<Void> resend(@AuthenticationPrincipal UserAccount currentUser) {
        verificationService.resend(currentUser);
        return ResponseEntity.accepted().build();
    }
}
