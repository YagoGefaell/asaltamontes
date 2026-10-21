package io.github.yagogefaell.asaltamontes.common.exceptions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Every error answers the right status with a ProblemDetail body (T04).
 */
class ErrorHandlingIT extends AbstractIntegrationTest {

    @Value("${asaltamontes.jwt.secret-key}")
    private String jwtSecret;

    @Nested
    class BadRequest {

        @Test
        void malformedJson() throws Exception {
            UserAccount user = testData.createUser();

            mockMvc.perform(post("/api/communities")
                            .with(auth.bearer(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }

        @Test
        void pathVariableOfWrongType() throws Exception {
            UserAccount user = testData.createUser();

            mockMvc.perform(post("/api/communities/not-a-number/join").with(auth.bearer(user)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void unknownSortProperty() throws Exception {
            UserAccount user = testData.createUser();

            mockMvc.perform(get("/api/communities").param("sort", "doesNotExist").with(auth.bearer(user)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void followYourself() throws Exception {
            UserAccount user = testData.createUser();

            mockMvc.perform(post("/api/users/follow/" + user.getUsername()).with(auth.bearer(user)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("You cannot follow yourself"));
        }
    }

    @Nested
    class Unauthorized {

        @Test
        void withoutToken() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("No autenticado"));
        }

        @Test
        void withExpiredToken() throws Exception {
            UserAccount user = testData.createUser();
            String expired = token(user.getId(), signingKey(jwtSecret), new Date(System.currentTimeMillis() - 60_000));

            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(containsString("Token")));
        }

        @Test
        void withTokenSignedByAnotherKey() throws Exception {
            UserAccount user = testData.createUser();
            String forged = token(user.getId(), signingKey("another-secret-key-that-is-long-enough-hs256"),
                    new Date(System.currentTimeMillis() + 60_000));

            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(containsString("Token")));
        }

        @Test
        void withGarbageToken() throws Exception {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void refreshWithoutCookie() throws Exception {
            mockMvc.perform(post("/auth/refresh"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Forbidden {

        @Test
        void readingAChatYouDoNotBelongTo() throws Exception {
            UserAccount ana = testData.createUser();
            UserAccount bea = testData.createUser();
            UserAccount outsider = testData.createUser();
            Long conversationId = testData.createPrivateConversation(ana, bea);

            mockMvc.perform(get("/api/conversations/" + conversationId + "/messages").with(auth.bearer(outsider)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }

        @Test
        void deletingACommunityYouDoNotOwn() throws Exception {
            UserAccount owner = testData.createUser();
            UserAccount other = testData.createUser();
            Community community = testData.createCommunity(owner);

            mockMvc.perform(delete("/api/communities/" + community.getId()).with(auth.bearer(other)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class NotFound {

        @Test
        void unknownCommunity() throws Exception {
            UserAccount user = testData.createUser();

            mockMvc.perform(get("/api/communities/" + testData.uniqueName("missing")).with(auth.bearer(user)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Comunidad no encontrada"));
        }

        @Test
        void unknownUser() throws Exception {
            UserAccount user = testData.createUser();

            mockMvc.perform(post("/api/users/follow/" + testData.uniqueName("ghost")).with(auth.bearer(user)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void unknownRoute() throws Exception {
            UserAccount user = testData.createUser();

            mockMvc.perform(get("/api/does-not-exist").with(auth.bearer(user)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Conflict {

        @Test
        void followingTwice() throws Exception {
            UserAccount ana = testData.createUser();
            UserAccount bea = testData.createUser();

            mockMvc.perform(post("/api/users/follow/" + bea.getUsername()).with(auth.bearer(ana)))
                    .andExpect(status().is2xxSuccessful());
            mockMvc.perform(post("/api/users/follow/" + bea.getUsername()).with(auth.bearer(ana)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Ya sigues al usuario: " + bea.getUsername()));
        }

        @Test
        void ownerLeavingTheirCommunity() throws Exception {
            UserAccount owner = testData.createUser();
            Community community = testData.createCommunity(owner);

            mockMvc.perform(post("/api/communities/" + community.getId() + "/leave").with(auth.bearer(owner)))
                    .andExpect(status().isConflict());
        }

        @Test
        void duplicateCommunityName() throws Exception {
            UserAccount owner = testData.createUser();
            Community community = testData.createCommunity(owner);

            mockMvc.perform(post("/api/communities")
                            .with(auth.bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"" + community.getName() + "\"}"))
                    .andExpect(status().isConflict());
        }
    }

    private static SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(Long userId, SecretKey key, Date expiration) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(expiration.getTime() - 120_000))
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
}
