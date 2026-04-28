package io.github.yagogefaell.asaltamontes.auth.controller;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.auth.dto.GoogleLoginRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.TokenPairs;
import io.github.yagogefaell.asaltamontes.auth.service.AuthService;
import io.github.yagogefaell.asaltamontes.common.cookies.CookieUtil;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        TokenPairs tokens = authService.login(request.username(), request.password());

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(tokens.refreshToken()).toString())
            .body(Map.of("accessToken", tokens.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("refreshToken", "/").toString())
            .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        String newAccessToken = authService.refreshAccessToken(refreshToken);
        
        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {

        UserAccount user = authService.register(request);

        TokenPairs tokens = authService.generateTokens(user);

        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(tokens.refreshToken()).toString())
            .body(Map.of("accessToken", tokens.accessToken()));
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest body) {
        String token = body.token();
        TokenPairs tokens = authService.googleLogin(token);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(tokens.refreshToken()).toString())
            .body(Map.of("accessToken", tokens.accessToken()));
    }
}
