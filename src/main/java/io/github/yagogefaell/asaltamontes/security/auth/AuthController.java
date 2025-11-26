package io.github.yagogefaell.asaltamontes.security.auth;

import io.github.yagogefaell.asaltamontes.security.auth.dto.*;
import io.github.yagogefaell.asaltamontes.security.jwt.JwtUtil;
import io.github.yagogefaell.asaltamontes.users.User;
import io.github.yagogefaell.asaltamontes.users.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthController(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    // ---------------- LOGIN ----------------
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request,
                                              AuthenticationConfiguration authConfig) throws Exception {
        AuthenticationManager authenticationManager = authConfig.getAuthenticationManager();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtUtil.generateAccessToken(userDetails.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
    }

    // ---------------- REFRESH TOKEN ----------------
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        String username = jwtUtil.extractUsername(request.refreshToken());

        if (!jwtUtil.isTokenValid(request.refreshToken(), username)) {
            return ResponseEntity.status(401).build();
        }

        String accessToken = jwtUtil.generateAccessToken(username);
        String refreshToken = jwtUtil.generateRefreshToken(username);

        return ResponseEntity.ok(new RefreshTokenResponse(accessToken, refreshToken));
    }

    // ---------------- REGISTRO ----------------
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.registerUser(request.name(), request.email(), request.password());

            String accessToken = jwtUtil.generateAccessToken(user.getEmail());
            String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

            return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new AuthResponse(null, null));
        }
    }
}
