package io.github.yagogefaell.asaltamontes.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.domain.RefreshSession;
import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.TokenPairs;
import io.github.yagogefaell.asaltamontes.auth.repository.RefreshSessionRepository;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidUsernameOrPasswordException;
import io.github.yagogefaell.asaltamontes.infrastructure.google.GoogleAuthService;
import io.github.yagogefaell.asaltamontes.infrastructure.google.GoogleTokenVerifier;
import io.github.yagogefaell.asaltamontes.infrastructure.security.JwtProperties;
import io.github.yagogefaell.asaltamontes.infrastructure.security.JwtUtil;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final GoogleAuthService googleAuthService;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RefreshSessionRepository refreshSessionRepository;

    public TokenPairs login(String username, String password) {
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            
            UserAccount user = (UserAccount) authentication.getPrincipal();
            return generateTokens(user);

        } catch (Exception e) {
            throw new InvalidUsernameOrPasswordException();
        }
    }

    @Transactional
    public TokenPairs googleLogin(String code) {
        String tokenId = googleAuthService.exchangeCodeForIdToken(code);

        var payload = googleTokenVerifier.verify(tokenId);

        UserAccount user = userService.findOrCreateGoogleUser(
                payload.getEmail(),
                (String) payload.get("name"),
                (String) payload.get("picture"),
                (String) payload.get("given_name")
        );

        return generateTokens(user);
    }

    public String refreshAccessToken(String refreshToken) {

        Claims claims = validateToken(refreshToken);

        if (!jwtUtil.isType(claims, JwtUtil.REFRESH_TYPE) || claims.getId() == null) {
            throw new InvalidTokenException("Token inválido");
        }

        // The session row disappears with the user (ON DELETE CASCADE), so this also covers deleted accounts
        boolean sessionActive = refreshSessionRepository.findById(claims.getId())
                .map(RefreshSession::isActive)
                .orElse(false);
        if (!sessionActive) {
            throw new InvalidTokenException("Sesión cerrada o caducada");
        }

        Long id = jwtUtil.extractId(claims);

        return jwtUtil.generateAccessToken(id);
    }

    public UserAccount verifyAccessToken(String accessToken) {

        Claims claims = validateToken(accessToken);

        if (!jwtUtil.isType(claims, JwtUtil.ACCESS_TYPE)) {
            throw new InvalidTokenException("Token inválido");
        }

        Long id = jwtUtil.extractId(claims);

        return loadUserById(id);
    }

    private Claims validateToken(String token) {

        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token no proporcionado");
        }

        try {
            Claims claims = jwtUtil.extractAllClaims(token);

            if (jwtUtil.isExpired(claims)) {
                throw new InvalidTokenException("Token expirado");
            }

            return claims;

        } catch (Exception e) {
            throw new InvalidTokenException("Token inválido");
        }
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        return userService.registerUserWithProfile(
                request.fullName(),
                request.username(),
                request.email(),
                request.password(),
                request.confirmPassword()
        );
    }

    public TokenPairs generateTokens(UserAccount user) {
        String jti = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), jti);

        refreshSessionRepository.save(new RefreshSession(
                jti,
                user,
                LocalDateTime.now().plus(Duration.ofMillis(jwtProperties.getRefreshExpiration()))
        ));

        return new TokenPairs(accessToken, refreshToken);
    }

    /** Revokes the session of this refresh token. Invalid or missing tokens are ignored: logout always succeeds. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims = jwtUtil.extractAllClaims(refreshToken);
            if (jwtUtil.isType(claims, JwtUtil.REFRESH_TYPE) && claims.getId() != null) {
                refreshSessionRepository.revoke(claims.getId(), LocalDateTime.now());
            }
        } catch (Exception e) {
            // Expired or tampered token: there is nothing to revoke
        }
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshSessionRepository.revokeAllByUserId(userId, LocalDateTime.now());
    }

    public UserAccount loadUserById(Long id) {
        return userService.loadUserById(id);
    }
}
