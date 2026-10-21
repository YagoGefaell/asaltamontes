package io.github.yagogefaell.asaltamontes.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import io.github.yagogefaell.asaltamontes.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class AuthControllerIT extends AbstractIntegrationTest {

    @Test
    void register_returnsCreatedWithAccessTokenAndRefreshCookie() throws Exception {
        String username = testData.uniqueName("newuser");
        RegisterRequest request = new RegisterRequest(
                "Nueva Usuaria", username, username + "@test.local", "password123", "password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }

    @Test
    void register_withMismatchedPasswords_returnsConflictWithFieldErrors() throws Exception {
        String username = testData.uniqueName("newuser");
        RegisterRequest request = new RegisterRequest(
                "Nueva Usuaria", username, username + "@test.local", "password123", "different123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.confirmPassword").isNotEmpty());
    }

    @Test
    void register_withUsernameThatOnlyDiffersInCase_returnsConflict() throws Exception {
        UserAccount existing = testData.createUser(testData.uniqueName("ana"));
        String sameNameUpperCase = existing.getUsername().toUpperCase();
        RegisterRequest request = new RegisterRequest(
                "Otra Ana", sameNameUpperCase, testData.uniqueName("other") + "@test.local", "password123", "password123");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.username").value("Username ya registrado"));
    }

    @Test
    void login_withValidCredentials_returnsAccessTokenAndRefreshCookie() throws Exception {
        UserAccount user = testData.createUser();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(user.getUsername(), TestDataFactory.DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")));
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        UserAccount user = testData.createUser();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(user.getUsername(), "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }
}
