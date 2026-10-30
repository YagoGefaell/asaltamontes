package io.github.yagogefaell.asaltamontes.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.servlet.http.Cookie;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;

/** T24b: password reset ("forgot my password") and change of email with verification of the new one (BUG-042). */
class PasswordResetAndEmailChangeIT extends AbstractIntegrationTest {

    private static final String NEW_PASSWORD = "brand-new-pass1";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAccountRepository users;

    // ---------------------------------------------------------------- password reset

    @Test
    void forgotPassword_sendsALink_andUsingItSetsThePassword_revokesSessionsAndWarns() throws Exception {
        UserAccount ana = testData.createUser();
        Cookie session = loginCookie(ana);
        mockMvc.perform(post("/auth/refresh").cookie(session)).andExpect(status().isOk());

        forgot(ana.getEmail()).andExpect(status().isAccepted());
        String token = mail.lastTokenSentTo(ana.getEmail());
        assertThat(mail.sentTo(ana.getEmail()).get(0).textBody()).contains("/reset-password?token=");

        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // New password works, the old one and the old session do not
        login(ana.getUsername(), NEW_PASSWORD).andExpect(status().isOk());
        login(ana.getUsername(), TestDataFactory.DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        // "Your password changed" notice (after the reset link itself)
        assertThat(mail.sentTo(ana.getEmail())).hasSize(2);
        assertThat(mail.sentTo(ana.getEmail()).get(1).subject()).contains("ha cambiado");
    }

    @Test
    void forgotPassword_answersTheSame_forUnknownAndKnownAddresses() throws Exception {
        UserAccount ana = testData.createUser();

        String known = forgot(ana.getEmail()).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = forgot("nobody-" + System.nanoTime() + "@test.local").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
        assertThat(mail.sentTo("nobody@test.local")).isEmpty();
    }

    @Test
    void forgotPassword_forAGoogleAccount_sendsNoLink() throws Exception {
        String email = testData.uniqueName("g") + "@gmail.test";
        UserAccount google = userServiceGoogle(email);

        forgot(email).andExpect(status().isAccepted());

        assertThat(mail.sentTo(email)).hasSize(1);
        assertThat(mail.sentTo(email).get(0).textBody()).doesNotContain("token=").contains("Google");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE user_id = ?", Integer.class, google.getId())).isZero();
    }

    @Test
    void forgotPassword_withAnInvalidAddress_isA400() throws Exception {
        forgot("not-an-email").andExpect(status().isBadRequest());
    }

    @Test
    void aResetTokenWorksOnlyOnce_andANewRequestReplacesIt() throws Exception {
        UserAccount ana = testData.createUser();
        forgot(ana.getEmail());
        String first = mail.lastTokenSentTo(ana.getEmail());
        forgot(ana.getEmail());
        String second = mail.lastTokenSentTo(ana.getEmail());

        reset(first, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(second, "another-pass-22", "another-pass-22").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace no es válido o ha caducado"));
    }

    @Test
    void anExpiredOrUnknownResetToken_failsWithTheSameError() throws Exception {
        UserAccount ana = testData.createUser();
        forgot(ana.getEmail());
        String token = mail.lastTokenSentTo(ana.getEmail());
        jdbc.update("UPDATE account_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?", ana.getId());

        String expired = reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String unknown = reset("no-such-token-0123456789", NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(expired).get("detail")).isEqualTo(objectMapper.readTree(unknown).get("detail"));
        login(ana.getUsername(), TestDataFactory.DEFAULT_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aWeakOrMismatchedPassword_doesNotSpendTheToken() throws Exception {
        UserAccount ana = testData.createUser();
        forgot(ana.getEmail());
        String token = mail.lastTokenSentTo(ana.getEmail());

        reset(token, "short", "short").andExpect(status().isBadRequest());
        reset(token, NEW_PASSWORD, "different-pass-1").andExpect(status().isConflict());

        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void theResetTokenIsNotStoredInTheClear_andOtherPurposesCannotUseIt() throws Exception {
        UserAccount ana = testData.createUser();
        forgot(ana.getEmail());
        String token = mail.lastTokenSentTo(ana.getEmail());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE token_hash = ?", Integer.class, token)).isZero();
        // A reset token does not verify the email nor confirm an email change
        mockMvc.perform(post("/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/auth/confirm-email-change").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token))))
                .andExpect(status().isBadRequest());
        reset(token, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void resettingThePassword_provesTheMailbox_soTheAccountBecomesVerified() throws Exception {
        UserAccount ana = testData.createUser();
        assertThat(users.findById(ana.getId()).orElseThrow().getIsVerified()).isFalse();
        forgot(ana.getEmail());

        reset(mail.lastTokenSentTo(ana.getEmail()), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(users.findById(ana.getId()).orElseThrow().getIsVerified()).isTrue();
    }

    // ---------------------------------------------------------------- change of email

    @Test
    void emailChange_isPendingUntilTheNewAddressConfirms_thenAppliesAndWarnsTheOldOne() throws Exception {
        UserAccount ana = testData.createUser();
        String oldEmail = ana.getEmail();
        String newEmail = testData.uniqueName("nuevo") + "@test.local";

        requestChange(ana, newEmail, TestDataFactory.DEFAULT_PASSWORD).andExpect(status().isAccepted());

        // Nothing changed yet; the link went to the NEW address and the old one was warned
        assertThat(users.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo(oldEmail);
        assertThat(mail.sentTo(newEmail)).hasSize(1);
        assertThat(mail.sentTo(newEmail).get(0).textBody()).contains("/confirm-email-change?token=");
        assertThat(mail.sentTo(oldEmail)).hasSize(1);
        assertThat(mail.sentTo(oldEmail).get(0).textBody()).doesNotContain("token=").doesNotContain(newEmail);

        confirmChange(mail.lastTokenSentTo(newEmail)).andExpect(status().isNoContent());

        UserAccount after = users.findById(ana.getId()).orElseThrow();
        assertThat(after.getEmail()).isEqualTo(newEmail);
        assertThat(after.getIsVerified()).isTrue();
        assertThat(mail.sentTo(oldEmail)).hasSize(2);
    }

    @Test
    void emailChange_needsTheCurrentPassword() throws Exception {
        UserAccount ana = testData.createUser();
        String newEmail = testData.uniqueName("nuevo") + "@test.local";

        requestChange(ana, newEmail, "wrong-password").andExpect(status().isForbidden());

        assertThat(mail.sentTo(newEmail)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_tokens WHERE user_id = ?", Integer.class, ana.getId())).isZero();
    }

    @Test
    void emailChange_requiresALoggedInUser() throws Exception {
        mockMvc.perform(post("/api/users/me/email-change").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newEmail", "x@test.local", "currentPassword", "password123")))).andExpect(status().isUnauthorized());
    }

    @Test
    void emailChange_toAnAddressInUse_orTheSameOne_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        requestChange(ana, bea.getEmail().toUpperCase(), TestDataFactory.DEFAULT_PASSWORD).andExpect(status().isConflict());
        requestChange(ana, ana.getEmail(), TestDataFactory.DEFAULT_PASSWORD).andExpect(status().isConflict());
        assertThat(mail.sentTo(bea.getEmail())).isEmpty();
    }

    @Test
    void emailChange_isRejectedIfSomeoneTookTheAddressMeanwhile() throws Exception {
        UserAccount ana = testData.createUser();
        String newEmail = testData.uniqueName("nuevo") + "@test.local";
        requestChange(ana, newEmail, TestDataFactory.DEFAULT_PASSWORD).andExpect(status().isAccepted());
        String token = mail.lastTokenSentTo(newEmail);
        UserAccount other = testData.createUser();
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", newEmail, other.getId());

        confirmChange(token).andExpect(status().isBadRequest());

        assertThat(users.findById(ana.getId()).orElseThrow().getEmail()).isNotEqualTo(newEmail);
    }

    @Test
    void anEmailChangeTokenWorksOnce_andANewRequestReplacesIt() throws Exception {
        UserAccount ana = testData.createUser();
        String first = testData.uniqueName("uno") + "@test.local";
        String second = testData.uniqueName("dos") + "@test.local";
        requestChange(ana, first, TestDataFactory.DEFAULT_PASSWORD);
        requestChange(ana, second, TestDataFactory.DEFAULT_PASSWORD);

        confirmChange(mail.lastTokenSentTo(first)).andExpect(status().isBadRequest());
        String token = mail.lastTokenSentTo(second);
        confirmChange(token).andExpect(status().isNoContent());
        confirmChange(token).andExpect(status().isBadRequest());
        assertThat(users.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo(second);
    }

    @Test
    void changingTheEmail_invalidatesPendingResetLinksSentToTheOldMailbox() throws Exception {
        UserAccount ana = testData.createUser();
        forgot(ana.getEmail());
        String oldResetToken = mail.lastTokenSentTo(ana.getEmail());
        String newEmail = testData.uniqueName("nuevo") + "@test.local";
        requestChange(ana, newEmail, TestDataFactory.DEFAULT_PASSWORD);
        confirmChange(mail.lastTokenSentTo(newEmail)).andExpect(status().isNoContent());

        reset(oldResetToken, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void resettingThePassword_cancelsAPendingEmailChange() throws Exception {
        UserAccount ana = testData.createUser();
        String newEmail = testData.uniqueName("nuevo") + "@test.local";
        requestChange(ana, newEmail, TestDataFactory.DEFAULT_PASSWORD);
        String changeToken = mail.lastTokenSentTo(newEmail);
        forgot(ana.getEmail());
        reset(mail.lastTokenSentTo(ana.getEmail()), NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        confirmChange(changeToken).andExpect(status().isBadRequest());
    }

    @Test
    void aGoogleAccountCannotChangeItsEmailThisWay() throws Exception {
        String email = testData.uniqueName("g") + "@gmail.test";
        UserAccount google = userServiceGoogle(email);

        requestChange(google, "otro@test.local", "whatever-pass").andExpect(status().isConflict());
    }

    @Test
    void patchingTheProfile_nolongerChangesTheEmail() throws Exception {
        UserAccount ana = testData.createUser();

        mockMvc.perform(patch("/api/users/me").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "taken-over@test.local", "bio", "hola")))).andExpect(status().isOk());

        assertThat(users.findById(ana.getId()).orElseThrow().getEmail()).isEqualTo(ana.getEmail());
    }

    // ---------------------------------------------------------------- helpers

    @Autowired private io.github.yagogefaell.asaltamontes.user.service.UserService userService;

    private UserAccount userServiceGoogle(String email) {
        return userService.findOrCreateGoogleUser(email, "Ana Google", null, "Ana");
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email))));
    }

    private ResultActions reset(String token, String password, String confirm) throws Exception {
        return mockMvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "password", password, "confirmPassword", confirm))));
    }

    private ResultActions requestChange(UserAccount user, String newEmail, String password) throws Exception {
        return mockMvc.perform(post("/api/users/me/email-change").with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newEmail", newEmail, "currentPassword", password))));
    }

    private ResultActions confirmChange(String token) throws Exception {
        return mockMvc.perform(post("/auth/confirm-email-change").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("token", token))));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", username, "password", password))));
    }

    private Cookie loginCookie(UserAccount user) throws Exception {
        String setCookie = login(user.getUsername(), TestDataFactory.DEFAULT_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String value = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        return new Cookie("refreshToken", value);
    }
}
