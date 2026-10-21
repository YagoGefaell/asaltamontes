package io.github.yagogefaell.asaltamontes.infrastructure.logging;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;

class RequestIdFilterIT extends AbstractIntegrationTest {

    @Test
    void generatesRequestIdWhenMissing() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void keepsValidIncomingRequestId() throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "abc-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "abc-123"));
    }

    @Test
    void replacesMalformedIncomingRequestId() throws Exception {
        mockMvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "bad id\nwith newline"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern("[0-9a-f-]{36}")));
    }
}
