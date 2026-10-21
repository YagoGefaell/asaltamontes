package io.github.yagogefaell.asaltamontes.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.auth.service.AuthService;
import io.github.yagogefaell.asaltamontes.common.cookies.CookieProperties;
import io.github.yagogefaell.asaltamontes.common.cookies.CookieUtil;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/sessions")
public class SessionController {

    private final AuthService authService;
    private final CookieProperties cookieProperties;

    /** "Log out everywhere": every refresh token of the user stops working, including this device's. */
    @PostMapping("/revoke-all")
    public ResponseEntity<Void> revokeAll(@AuthenticationPrincipal UserAccount currentUser) {
        authService.revokeAllSessions(currentUser.getId());

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("refreshToken", "/", cookieProperties).toString())
            .build();
    }
}
