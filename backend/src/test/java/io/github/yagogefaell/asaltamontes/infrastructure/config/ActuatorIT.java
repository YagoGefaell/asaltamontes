package io.github.yagogefaell.asaltamontes.infrastructure.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class ActuatorIT extends AbstractIntegrationTest {

    @Test
    void health_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessAndReadinessProbes_arePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void otherEndpoints_areNotExposedEvenWhenAuthenticated() throws Exception {
        UserAccount user = testData.createUser();

        // Not exposed means no actuator data. Today unknown routes answer 500 instead of 404 (BUG-039, T04).
        mockMvc.perform(get("/actuator/env").with(auth.bearer(user)))
                .andExpect(status().is(not(200)))
                .andExpect(content().string(not(containsString("propertySources"))));
        mockMvc.perform(get("/actuator/beans").with(auth.bearer(user)))
                .andExpect(status().is(not(200)))
                .andExpect(content().string(not(containsString("contexts"))));
    }
}
