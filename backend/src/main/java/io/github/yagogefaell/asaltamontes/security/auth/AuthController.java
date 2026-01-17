package io.github.yagogefaell.asaltamontes.security.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.security.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.security.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.security.jwt.JwtUtil;
import io.github.yagogefaell.asaltamontes.user.account.UserAccount;
import io.github.yagogefaell.asaltamontes.utils.CookieUtil;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    

    public AuthController(JwtUtil jwtUtil, AuthService authService, AuthenticationManager authenticationManager) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
        this.authenticationManager = authenticationManager;
    }

    // ---------------- LOGIN ----------------
    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String accessToken = jwtUtil.generateAccessToken(userDetails.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(accessToken).toString());
        headers.add(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(refreshToken).toString());

        return ResponseEntity.ok()
                .headers(headers)
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
        String username = jwtUtil.extractUsername(refreshToken);

        if (!jwtUtil.isTokenValid(refreshToken, username)) {
            return ResponseEntity.status(401).build();
        }

        String newAccessToken = jwtUtil.generateAccessToken(username);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(newAccessToken).toString())
            .build();
    }

    // ---------------- REGISTRO ----------------
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {

        UserAccount user = authService.register(request);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(refreshToken).toString())
            .build();
    }

    // ---------------- VERIFICAR TOKEN ----------------
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyToken(
            @CookieValue(name = "accessToken", required = false) String accessToken) {

        try {
            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.status(401).build();
            }

            String username = jwtUtil.extractUsername(accessToken);

            // Si el token no es válido
            if (!jwtUtil.isTokenValid(accessToken, username)) {
                return ResponseEntity.status(401).build();
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}
