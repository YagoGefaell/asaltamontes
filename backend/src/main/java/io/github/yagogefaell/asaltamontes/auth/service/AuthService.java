package io.github.yagogefaell.asaltamontes.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.TokenPairs;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidUsernameOrPasswordException;
import io.github.yagogefaell.asaltamontes.infrastructure.google.GoogleTokenVerifier;
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
    private final JwtUtil jwtUtil;

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
    public TokenPairs googleLogin(String idToken) {
        var payload = googleTokenVerifier.verify(idToken);

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

        Long id = jwtUtil.extractId(claims);

        return jwtUtil.generateAccessToken(id);
    }

    public UserAccount verifyAccessToken(String accessToken) {

        Claims claims = validateToken(accessToken);

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
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        return new TokenPairs(accessToken, refreshToken);
    }

    public UserAccount loadUserById(Long id) {
        return userService.loadUserById(id);
    }
}
