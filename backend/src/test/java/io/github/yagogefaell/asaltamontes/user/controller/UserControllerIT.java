package io.github.yagogefaell.asaltamontes.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class UserControllerIT extends AbstractIntegrationTest {

    @Test
    void getMe_withToken_returnsOwnProfile() throws Exception {
        UserAccount user = testData.createUser();

        mockMvc.perform(get("/api/users/me").with(auth.bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.getUsername()));
    }

    @Test
    void getMe_withoutToken_isRejected() throws Exception {
        // Current behaviour: Spring Security's default entry point answers 403. T04 will change it to 401.
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }
}
