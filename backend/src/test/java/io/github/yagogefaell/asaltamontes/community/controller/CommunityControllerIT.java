package io.github.yagogefaell.asaltamontes.community.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import io.github.yagogefaell.asaltamontes.community.dto.CreateCommunityDto;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class CommunityControllerIT extends AbstractIntegrationTest {

    @Test
    void createCommunity_thenItAppearsInTheList() throws Exception {
        UserAccount owner = testData.createUser();
        String name = testData.uniqueName("Trail");

        mockMvc.perform(post("/api/communities")
                        .with(auth.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateCommunityDto(name, "Salidas los domingos", null))))
                .andExpect(status().isCreated());

        // Lists via /api/communities: /api/communities/me currently fails with 500 (BUG-012, fixed in T14).
        mockMvc.perform(get("/api/communities").param("size", "1000").with(auth.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(hasItem(name)));
    }

    @Test
    void createCommunity_withBlankName_returnsBadRequest() throws Exception {
        UserAccount owner = testData.createUser();

        mockMvc.perform(post("/api/communities")
                        .with(auth.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateCommunityDto("", null, null))))
                .andExpect(status().isBadRequest());
    }
}
