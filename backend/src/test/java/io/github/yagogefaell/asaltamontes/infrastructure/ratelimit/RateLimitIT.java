package io.github.yagogefaell.asaltamontes.infrastructure.ratelimit;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.yagogefaell.asaltamontes.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.TestDataFactory;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

@TestPropertySource(properties = "asaltamontes.rate-limit.enabled=true")
class RateLimitIT extends AbstractIntegrationTest {

    @Test
    void sixthLoginInAMinuteFromTheSameIp_isRejectedWith429() throws Exception {
        String ip = "10.0.0.1";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(testData.uniqueName("nobody"), "wrong-password").with(from(ip)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login(testData.uniqueName("nobody"), "wrong-password").with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")))
                .andExpect(jsonPath("$.title").value("Too Many Requests"));
    }

    @Test
    void sixthLoginInAMinuteForTheSameUsername_isRejectedEvenFromDifferentIps() throws Exception {
        UserAccount user = testData.createUser();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(user.getUsername(), "wrong-password").with(from("10.0.1." + i)))
                    .andExpect(status().isUnauthorized());
        }

        // Even with the right password: the account is locked for the rest of the minute
        mockMvc.perform(login(user.getUsername().toUpperCase(), TestDataFactory.DEFAULT_PASSWORD).with(from("10.0.1.99")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginsBelowTheLimit_stillWork() throws Exception {
        UserAccount user = testData.createUser();

        mockMvc.perform(login(user.getUsername(), TestDataFactory.DEFAULT_PASSWORD).with(from("10.0.2.1")))
                .andExpect(status().isOk());
    }

    @Test
    void fourthRegistrationInAMinuteFromTheSameIp_isRejected() throws Exception {
        String ip = "10.0.3.1";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(register().with(from(ip))).andExpect(status().isCreated());
        }

        mockMvc.perform(register().with(from(ip))).andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder login(String username, String password) throws Exception {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(new LoginRequest(username, password)));
    }

    private MockHttpServletRequestBuilder register() throws Exception {
        String username = testData.uniqueName("reg");
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(
                new RegisterRequest("Nueva Usuaria", username, username + "@test.local", "password123", "password123")));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
