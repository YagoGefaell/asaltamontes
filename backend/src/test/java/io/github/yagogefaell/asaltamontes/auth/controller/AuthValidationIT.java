package io.github.yagogefaell.asaltamontes.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.yagogefaell.asaltamontes.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;

/**
 * T06b: registration rules (400 with field errors) shared with the frontend.
 */
class AuthValidationIT extends AbstractIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void register_withInvalidFields_returnsBadRequestWithEveryFieldError() throws Exception {
        RegisterRequest request = new RegisterRequest("A1", ".ana", "not-an-email", "short", "short");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").isNotEmpty())
                .andExpect(jsonPath("$.errors.username").isNotEmpty())
                .andExpect(jsonPath("$.errors.email").value("Formato de email inválido"))
                .andExpect(jsonPath("$.errors.password").value("Debe tener entre 8 y 100 caracteres"));
    }

    @Test
    void register_withUsernameLongerThan30_returnsBadRequest() throws Exception {
        String username = "a".repeat(31);
        RegisterRequest request = new RegisterRequest("Ana", username, testData.uniqueName("u") + "@test.local", "password123", "password123");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("Debe tener entre 3 y 30 caracteres"));
    }

    @Test
    void register_acceptsNamesWithAccentsHyphensAndApostrophes() throws Exception {
        String username = testData.uniqueName("maria.jose");
        RegisterRequest request = new RegisterRequest(
                "María-José O'Neill", username, username + "@test.local", "password123", "password123");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void login_withEmptyFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountsWithAShortPasswordFromBeforeTheNewRules_canStillLogIn() throws Exception {
        UserAccount legacy = testData.createUser();
        legacy.setPasswordHash(passwordEncoder.encode("123456"));
        userAccountRepository.save(legacy);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(legacy.getUsername(), "123456"))))
                .andExpect(status().isOk());
    }
}
