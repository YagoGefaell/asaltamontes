package io.github.yagogefaell.asaltamontes.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.yagogefaell.asaltamontes.auth.service.EmailVerificationService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T24b / BUG-084: limits on the account emails (reset, change, verification), with rate limiting switched on. */
@TestPropertySource(properties = {
        "asaltamontes.rate-limit.enabled=true",
        "asaltamontes.rate-limit.password-reset-per-minute=3",
        "asaltamontes.rate-limit.password-reset-requests-per-hour=2",
        "asaltamontes.rate-limit.email-change-requests-per-hour=3",
        "asaltamontes.rate-limit.emails-per-address-per-hour=2"})
class AccountEmailLimitsIT extends AbstractIntegrationTest {

    @Autowired private EmailVerificationService verificationService;

    @Test
    void forgotPassword_isLimitedPerIp_with429AndRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            forgot("nobody" + i + "@test.local", "10.1.0.1").andExpect(status().isAccepted());
        }
        forgot("nobody9@test.local", "10.1.0.1").andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        forgot("nobody9@test.local", "10.1.0.2").andExpect(status().isAccepted());
    }

    @Test
    void forgotPassword_isLimitedPerAddress_whetherOrNotItExists() throws Exception {
        UserAccount ana = testData.createUser();
        String unknown = "ghost-" + System.nanoTime() + "@test.local";

        for (String email : new String[] {ana.getEmail(), unknown}) {
            forgot(email, "10.2.0." + email.length()).andExpect(status().isAccepted());
            forgot(email, "10.3.0." + email.length()).andExpect(status().isAccepted());
            forgot(email, "10.4.0." + email.length()).andExpect(status().isTooManyRequests());
        }
    }

    @Test
    void noMoreThanTheCapOfAccountEmailsGoToOneAddress_acrossPurposes() throws Exception {
        UserAccount ana = testData.createUser();

        verificationService.sendVerificationEmail(ana);
        verificationService.sendVerificationEmail(ana);
        verificationService.sendVerificationEmail(ana);
        assertThat(mail.sentTo(ana.getEmail())).hasSize(2);

        // Verification emails used up the shared cap, but a legitimate reset still goes out (own quota)
        forgot(ana.getEmail(), "10.5.0.1").andExpect(status().isAccepted());
        assertThat(mail.sentTo(ana.getEmail())).hasSize(3);
        // "+tag" aliases share the cap of the mailbox
        UserAccount bea = testData.createUser();
        String alias = bea.getEmail().replace("@", "+x@");
        assertThat(alias).isNotEqualTo(bea.getEmail());
        change(ana, alias).andExpect(status().isAccepted());
        change(ana, bea.getEmail().replace("@", "+y@")).andExpect(status().isAccepted());
        change(ana, bea.getEmail().replace("@", "+z@")).andExpect(status().isConflict());
    }

    @Test
    void emailChange_cannotBeUsedToFloodAThirdParty() throws Exception {
        UserAccount ana = testData.createUser();
        String victim = testData.uniqueName("victima") + "@test.local";

        change(ana, victim).andExpect(status().isAccepted());
        change(ana, victim).andExpect(status().isAccepted());
        change(ana, victim).andExpect(status().isConflict());

        assertThat(mail.sentTo(victim)).hasSize(2);
    }

    @Test
    void emailChange_attemptsAreLimited_soThePasswordCannotBeGuessed() throws Exception {
        UserAccount ana = testData.createUser();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/users/me/email-change").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("newEmail", "x" + i + "@test.local", "currentPassword", "guess-" + i))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/users/me/email-change").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newEmail", "x9@test.local", "currentPassword", TestDataFactory.DEFAULT_PASSWORD))))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    private ResultActions change(UserAccount user, String newEmail) throws Exception {
        return mockMvc.perform(post("/api/users/me/email-change").with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("newEmail", newEmail, "currentPassword", TestDataFactory.DEFAULT_PASSWORD))));
    }

    private ResultActions forgot(String email, String ip) throws Exception {
        RequestPostProcessor from = request -> {
            request.setRemoteAddr(ip);
            return request;
        };
        return mockMvc.perform(post("/auth/forgot-password").with(from).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email))));
    }
}
