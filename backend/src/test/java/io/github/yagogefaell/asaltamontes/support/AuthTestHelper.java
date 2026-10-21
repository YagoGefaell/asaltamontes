package io.github.yagogefaell.asaltamontes.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.yagogefaell.asaltamontes.infrastructure.security.JwtUtil;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import lombok.RequiredArgsConstructor;

/**
 * Issues real access tokens (signed with the test secret) without going through /auth/login.
 */
@TestComponent
@RequiredArgsConstructor
public class AuthTestHelper {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public String loginAs(String username) {
        UserAccount user = (UserAccount) userService.loadUserByUsername(username);
        return jwtUtil.generateAccessToken(user.getId());
    }

    public String loginAs(UserAccount user) {
        return jwtUtil.generateAccessToken(user.getId());
    }

    /** MockMvc post-processor that adds {@code Authorization: Bearer <token>} for the given user. */
    public RequestPostProcessor bearer(UserAccount user) {
        String token = loginAs(user);
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }
}
