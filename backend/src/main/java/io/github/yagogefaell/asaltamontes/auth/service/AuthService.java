package io.github.yagogefaell.asaltamontes.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.TokenPairs;
import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import io.github.yagogefaell.asaltamontes.infrastructure.security.JwtUtil;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public TokenPairs login(String username, String password) {
        UserAccount userFromDb = (UserAccount) userService.loadUserByUsername(username);
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userFromDb, password)
        );

        UserAccount user = (UserAccount) authentication.getPrincipal();

        return generateTokens(user);
    }

    public String refreshAccessToken(String refreshToken) {
        Long id = jwtUtil.extractId(refreshToken);

        UserAccount user = loadUserById(id);

        if (!jwtUtil.isTokenValid(refreshToken, user)) {
            throw new InvalidTokenException("Refresh token inválido o expirado");
        }

        return jwtUtil.generateAccessToken(user.getId());
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

    public UserAccount verifyAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new InvalidTokenException("Access token no proporcionado");
        }

        Long id = jwtUtil.extractId(accessToken);
        UserAccount user = loadUserById(id);

        if (!jwtUtil.isTokenValid(accessToken, user)) {
            throw new InvalidTokenException("Access token inválido o expirado");
        }

        return user;
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
