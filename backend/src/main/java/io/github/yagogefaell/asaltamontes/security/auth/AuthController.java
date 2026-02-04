package io.github.yagogefaell.asaltamontes.security.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.security.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.security.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.security.auth.dto.TokenPairs;
import io.github.yagogefaell.asaltamontes.user.account.UserAccount;
import io.github.yagogefaell.asaltamontes.utils.CookieUtil;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    // ---------------- LOGIN ----------------
    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequest request) {
        TokenPairs tokens = authService.login(request.username(), request.password());

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(tokens.accessToken()).toString())
            .header(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(tokens.refreshToken()).toString())
            .build();
    }

    // ---------------- LOGOUT ----------------
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("accessToken", "/").toString())
            .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("refreshToken", "/").toString())
            .build();
    }

    // ---------------- REFRESH TOKEN ----------------
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue("refreshToken") String refreshToken) {
        String newAccessToken = authService.refreshAccessToken(refreshToken);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(newAccessToken).toString())
            .build();
    }

    // ---------------- REGISTRO ----------------
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {

        UserAccount user = authService.register(request);

        TokenPairs tokens = authService.generateTokens(user);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(tokens.accessToken()).toString())
            .header(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(tokens.refreshToken()).toString())
            .build();
    }

    // ---------------- VERIFICAR TOKEN ----------------
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyToken(
            @CookieValue(name = "accessToken", required = false) String accessToken) {

        authService.verifyAccessToken(accessToken);
        
        return ResponseEntity.ok().build();
    }
}
