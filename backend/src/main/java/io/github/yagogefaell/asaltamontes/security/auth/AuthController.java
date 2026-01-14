package io.github.yagogefaell.asaltamontes.security.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.security.auth.dto.AuthResponse;
import io.github.yagogefaell.asaltamontes.security.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.security.auth.dto.RefreshTokenRequest;
import io.github.yagogefaell.asaltamontes.security.auth.dto.RefreshTokenResponse;
import io.github.yagogefaell.asaltamontes.security.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.security.jwt.JwtUtil;
import io.github.yagogefaell.asaltamontes.users.User;
import io.github.yagogefaell.asaltamontes.users.UserServiceImpl;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserServiceImpl userService;
    private final AuthenticationManager authenticationManager;
    

    public AuthController(JwtUtil jwtUtil, UserServiceImpl userService, AuthenticationManager authenticationManager) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    // ---------------- LOGIN ----------------
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String accessToken = jwtUtil.generateAccessToken(userDetails.getUsername());
            String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

            return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(new AuthResponse(null, null));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new AuthResponse(null, null));
        }
    }

    // ---------------- REFRESH TOKEN ----------------
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        String username = jwtUtil.extractUsername(request.refreshToken());

        if (!jwtUtil.isTokenValid(request.refreshToken(), username)) {
            return ResponseEntity.status(401).build();
        }

        String accessToken = jwtUtil.generateAccessToken(username);

        return ResponseEntity.ok(new RefreshTokenResponse(accessToken));
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

    // ---------------- VERIFICAR TOKEN ----------------
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyToken(@RequestHeader("Authorization") String authHeader) {
        try {
            System.out.println("Authorization header: " + authHeader);
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).build();
            }

            String token = authHeader.substring(7);
            String email = jwtUtil.extractUsername(token);

            if (!jwtUtil.isTokenValid(token, email)) {
                return ResponseEntity.status(401).build();
            }

            // ✅ Token válido, no devolvemos datos
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

}
