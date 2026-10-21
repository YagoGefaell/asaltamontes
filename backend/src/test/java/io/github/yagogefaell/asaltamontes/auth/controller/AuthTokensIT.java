package io.github.yagogefaell.asaltamontes.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;

/**
 * T06a: access and refresh tokens are not interchangeable and refresh sessions can be revoked.
 */
class AuthTokensIT extends AbstractIntegrationTest {

    @Value("${asaltamontes.jwt.secret-key}")
    private String jwtSecret;

    @Test
    void refreshToken_cannotBeUsedAsBearer() throws Exception {
        Tokens tokens = login(testData.createUser());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.refresh()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessToken_cannotBeUsedToRefresh() throws Exception {
        Tokens tokens = login(testData.createUser());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", tokens.access())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validRefreshToken_issuesANewAccessToken() throws Exception {
        Tokens tokens = login(testData.createUser());

        MvcResult result = mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", tokens.refresh())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String newAccess = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Test
    void afterLogout_theOldRefreshTokenIsRejected() throws Exception {
        Tokens tokens = login(testData.createUser());

        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refreshToken", tokens.refresh())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", tokens.refresh())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutOrWithInvalidCookie_stillSucceeds() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isOk());
        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refreshToken", "garbage")))
                .andExpect(status().isOk());
    }

    @Test
    void revokeAll_rejectsEveryRefreshTokenOfTheUserOnly() throws Exception {
        UserAccount user = testData.createUser();
        UserAccount other = testData.createUser();
        Tokens laptop = login(user);
        Tokens phone = login(user);
        Tokens otherUser = login(other);

        mockMvc.perform(post("/api/users/me/sessions/revoke-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop.access()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", laptop.refresh())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", phone.refresh())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", otherUser.refresh())))
                .andExpect(status().isOk());
    }

    @Test
    void refreshTokenIssuedBeforeT06a_isRejected() throws Exception {
        UserAccount user = testData.createUser();
        // Same shape as the old JwtUtil.generateRefreshToken: valid signature, no "typ" and no jti
        String legacy = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", legacy)))
                .andExpect(status().isUnauthorized());
    }

    private Tokens login(UserAccount user) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(user.getUsername(), TestDataFactory.DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie refreshCookie = result.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        return new Tokens(body.get("accessToken").asText(), refreshCookie.getValue());
    }

    private record Tokens(String access, String refresh) { }
}
