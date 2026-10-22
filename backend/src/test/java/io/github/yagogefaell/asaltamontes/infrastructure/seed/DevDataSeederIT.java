package io.github.yagogefaell.asaltamontes.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.service.FollowService;
import io.github.yagogefaell.asaltamontes.user.service.UserService;

/**
 * T02b: the seed creates a usable demo once, and running it again changes nothing.
 * The test database is shared (it already has users), so the seeder runs with force=true.
 */
class DevDataSeederIT extends AbstractIntegrationTest {

    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private UserService userService;
    @Autowired private FollowService followService;
    @Autowired private CommunityService communityService;
    @Autowired private MessageService messageService;

    @Test
    void seedsADemoYouCanLogInto_andIsIdempotent() throws Exception {
        DevDataSeeder seeder = new DevDataSeeder(userAccountRepository, userService, followService, communityService, messageService);
        ReflectionTestUtils.setField(seeder, "force", true);

        seeder.run();
        long usersAfterFirstRun = userAccountRepository.count();
        seeder.run();

        assertThat(userAccountRepository.count()).isEqualTo(usersAfterFirstRun);

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"" + DevDataSeeder.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(login).get("accessToken").asText();

        mockMvc.perform(get("/api/communities/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Trail Vigo')].role").value(org.hamcrest.Matchers.hasItem("OWNER")));
        mockMvc.perform(get("/api/communities/trail-vigo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String chats = mockMvc.perform(get("/api/conversations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 8 private chats + the group (a plain list, or a page with "items" since T10b)
        var chatList = objectMapper.readTree(chats);
        assertThat((chatList.isArray() ? chatList : chatList.get("items")).size()).isGreaterThanOrEqualTo(9);
    }
}
