package io.github.yagogefaell.asaltamontes.infrastructure.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        mockMvc.perform(get("/actuator/env").with(auth.bearer(user))).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").with(auth.bearer(user))).andExpect(status().isNotFound());
    }
}
