package io.github.yagogefaell.asaltamontes.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.auth.service.EmailVerificationService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;

/** T24a: verification of the email (BUG-025) and the limits of unverified accounts. */
@TestPropertySource(properties = "asaltamontes.verification.enforce=true")
class EmailVerificationIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository users;
    @Autowired private EmailVerificationService verificationService;
    @Autowired private io.github.yagogefaell.asaltamontes.user.service.UserService userService;

    // ---------------------------------------------------------------- the flow

    @Test
    void registering_sendsTheLink_andUsingIt_verifiesTheAccount() throws Exception {
        String username = testData.uniqueName("nueva");
        register(username).andExpect(status().isCreated());
        String email = username + "@test.local";
        assertThat(isVerified(username)).isFalse();
        assertThat(mail.sentTo(email)).hasSize(1);
        assertThat(mail.sentTo(email).get(0).textBody()).contains("/verify-email?token=");

        verify(mail.lastTokenSentTo(email)).andExpect(status().isNoContent());

        assertThat(isVerified(username)).isTrue();
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        UserAccount ana = testData.createUser();
        verificationService.sendVerificationEmail(ana);
        String token = mail.lastTokenSentTo(ana.getEmail());

        verify(token).andExpect(status().isNoContent());
        verify(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace no es válido o ha caducado"));
    }

    @Test
    void aNewTokenReplacesThePreviousOne() throws Exception {
        UserAccount ana = testData.createUser();
        verificationService.sendVerificationEmail(ana);
        String first = mail.lastTokenSentTo(ana.getEmail());
        verificationService.sendVerificationEmail(ana);
        String second = mail.lastTokenSentTo(ana.getEmail());

        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE user_id = ?", Integer.class, ana.getId())).isEqualTo(1);
        verify(first).andExpect(status().isBadRequest());
        assertThat(isVerified(ana.getUsername())).isFalse();
        verify(second).andExpect(status().isNoContent());
    }

    @Test
    void anExpiredToken_failsWithTheSameErrorAsAnUnknownOne() throws Exception {
        UserAccount ana = testData.createUser();
        verificationService.sendVerificationEmail(ana);
        String token = mail.lastTokenSentTo(ana.getEmail());
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?", ana.getId());

        String expired = verify(token).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String unknown = verify("no-such-token-0123456789").andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(expired).get("detail")).isEqualTo(objectMapper.readTree(unknown).get("detail"));
        assertThat(isVerified(ana.getUsername())).isFalse();
    }

    @Test
    void theDatabaseNeverHoldsTheTokenInTheClear() {
        UserAccount ana = testData.createUser();
        verificationService.sendVerificationEmail(ana);
        String token = mail.lastTokenSentTo(ana.getEmail());

        String stored = jdbc.queryForObject("SELECT token_hash FROM account_tokens WHERE user_id = ?", String.class, ana.getId());

        assertThat(stored).isNotEqualTo(token).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE token_hash = ?", Integer.class, token)).isZero();
    }

    @Test
    void aResetPasswordTokenDoesNotVerifyTheEmail() throws Exception {
        UserAccount ana = testData.createUser();
        jdbc.update("INSERT INTO account_tokens (user_id, purpose, token_hash, expires_at) VALUES (?, 'RESET_PASSWORD', ?, now() + interval '1 hour')",
                ana.getId(), "a".repeat(64));

        // Even knowing a hash is impossible to invert, the purpose is part of the lookup
        verify("whatever").andExpect(status().isBadRequest());
        assertThat(isVerified(ana.getUsername())).isFalse();
    }

    @Test
    void anUnverifiedAccountCanLogIn() throws Exception {
        UserAccount ana = testData.createUser();

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ana.getUsername(), "password", TestDataFactory.DEFAULT_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void aGoogleAccountIsVerifiedFromTheStart() {
        String email = testData.uniqueName("g") + "@gmail.test";

        UserAccount google = userService.findOrCreateGoogleUser(email, "Ana Google", null, "Ana");

        assertThat(isVerified(google.getUsername())).isTrue();
        assertThat(mail.sentTo(email)).isEmpty();
    }

    // ---------------------------------------------------------------- resend

    @Test
    void resend_sendsAnotherLink_toTheLoggedInAccountOnly() throws Exception {
        UserAccount ana = testData.createUser();

        mockMvc.perform(post("/api/users/me/verification-email").with(auth.bearer(ana))).andExpect(status().isAccepted());
        assertThat(mail.sentTo(ana.getEmail())).hasSize(1);

        mockMvc.perform(post("/api/users/me/verification-email")).andExpect(status().isUnauthorized());
    }

    @Test
    void resend_whenAlreadyVerified_isAConflict() throws Exception {
        UserAccount ana = verified(testData.createUser());

        mockMvc.perform(post("/api/users/me/verification-email").with(auth.bearer(ana))).andExpect(status().isConflict());
        assertThat(mail.sentTo(ana.getEmail())).isEmpty();
    }

    // ---------------------------------------------------------------- limits of unverified accounts

    @Test
    void anUnverifiedAccountCannotCreateCommunitiesEventsPostsOrNewChats() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        notVerified(mockMvc.perform(post("/api/communities").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", testData.uniqueName("club"), "description", "x")))));
        notVerified(mockMvc.perform(post("/api/events").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", "Quedada", "type", "MEETUP", "startsAt", Instant.now().plus(3, ChronoUnit.DAYS).toString(),
                        "timezone", "Europe/Madrid")))));
        notVerified(mockMvc.perform(post("/api/posts").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("body", "Hola")))));
        notVerified(mockMvc.perform(post("/api/conversations/private").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", bea.getUsername())))));
        notVerified(mockMvc.perform(post("/api/conversations/group").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("groupName", "Grupo", "users", List.of(bea.getUsername()))))));
    }

    @Test
    void aVerifiedAccountCanDoAllOfIt() throws Exception {
        UserAccount ana = verified(testData.createUser());
        UserAccount bea = testData.createUser();

        mockMvc.perform(post("/api/communities").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", testData.uniqueName("club"), "description", "x")))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/posts").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("body", "Hola")))).andExpect(status().isCreated());
        mockMvc.perform(post("/api/conversations/private").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", bea.getUsername())))).andExpect(status().isOk());
    }

    @Test
    void anExistingChatKeepsWorkingForAnUnverifiedAccount() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        // Created before the account lost verification rights (or by the other side)
        jdbcCreatePrivateChat(ana, bea);

        mockMvc.perform(post("/api/conversations/private").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", bea.getUsername())))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private void jdbcCreatePrivateChat(UserAccount a, UserAccount b) {
        UserAccount verifiedA = verified(a);
        mockMvcQuiet(verifiedA, b);
        a.setIsVerified(false);
        users.save(a);
    }

    private void mockMvcQuiet(UserAccount a, UserAccount b) {
        try {
            mockMvc.perform(post("/api/conversations/private").with(auth.bearer(a)).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("username", b.getUsername())))).andExpect(status().isOk());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void notVerified(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.detail").value("Verifica tu email para poder hacer esto"));
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token))));
    }

    private ResultActions register(String username) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(new RegisterRequest("Nueva Usuaria", username, username + "@test.local", "password123", "password123"))));
    }

    private UserAccount verified(UserAccount user) {
        user.setIsVerified(true);
        return users.save(user);
    }

    private boolean isVerified(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow().getIsVerified();
    }
}
