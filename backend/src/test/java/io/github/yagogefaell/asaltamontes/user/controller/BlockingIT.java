package io.github.yagogefaell.asaltamontes.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserProfileRepository;
import io.github.yagogefaell.asaltamontes.user.service.BlockService;

class BlockingIT extends AbstractIntegrationTest {

    @Autowired
    private MessageService messageService;
    @Autowired
    private UserProfileRepository profiles;
    @Autowired
    private BlockService blocks;

    private void block(UserAccount by, UserAccount target) throws Exception {
        mockMvc.perform(post("/api/users/{u}/block", target.getUsername()).with(auth.bearer(by)))
                .andExpect(status().isNoContent());
    }

    private void follow(UserAccount who, UserAccount target) throws Exception {
        mockMvc.perform(post("/api/users/follow/{u}", target.getUsername()).with(auth.bearer(who)))
                .andExpect(status().isOk());
    }

    private void setPolicy(UserAccount user, String policy) throws Exception {
        mockMvc.perform(patch("/api/users/me").with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dmPolicy", policy))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dmPolicy").value(policy));
    }

    private int openPrivateChat(UserAccount from, UserAccount to) throws Exception {
        return mockMvc.perform(post("/api/conversations/private").with(auth.bearer(from))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("username", to.getUsername()))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void afterBlocking_neitherSideCanSendInTheExistingPrivateChat() {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID chat = testData.createPrivateConversation(ana, bea);
        messageService.processIncomingMessage(chat, ana.getId(), "hola");

        blocks.block(ana.getId(), bea.getUsername());

        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, ana.getId(), "hola"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> messageService.processIncomingMessage(chat, bea.getId(), "hola"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageNotContaining(ana.getUsername());

        blocks.unblock(ana.getId(), bea.getUsername());
        messageService.processIncomingMessage(chat, bea.getId(), "de nuevo");
    }

    @Test
    void afterBlocking_nobodyCanOpenAPrivateChat_andThereIsNoLeakOfWhoBlocked() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        testData.createPrivateConversation(ana, cris);

        block(ana, bea);

        assertThat(openPrivateChat(bea, ana)).isEqualTo(403);
        assertThat(openPrivateChat(ana, bea)).isEqualTo(403);
        // Blocks only affect the pair
        assertThat(openPrivateChat(bea, cris)).isEqualTo(200);

        // Also an already existing chat can no longer be "opened" through the create-or-get endpoint
        block(ana, cris);
        assertThat(openPrivateChat(cris, ana)).isEqualTo(403);
    }

    @Test
    void blocking_removesFollowsInBothDirections_andFixesCounters() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        follow(ana, bea);
        follow(bea, ana);
        follow(cris, ana);

        block(ana, bea);

        assertThat(profiles.findByUserId(ana.getId()).orElseThrow().getFollowersCount()).isEqualTo(1);
        assertThat(profiles.findByUserId(ana.getId()).orElseThrow().getFollowingCount()).isZero();
        assertThat(profiles.findByUserId(bea.getId()).orElseThrow().getFollowersCount()).isZero();
        assertThat(profiles.findByUserId(bea.getId()).orElseThrow().getFollowingCount()).isZero();

        // And they cannot follow each other while the block lasts
        mockMvc.perform(post("/api/users/follow/{u}", ana.getUsername()).with(auth.bearer(bea)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/follow/{u}", bea.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockedUser_getsA404ForTheBlockersProfile_andTheBlockerStillSeesHers() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        block(ana, bea);

        mockMvc.perform(get("/api/users/{u}", ana.getUsername()).with(auth.bearer(bea)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/{u}", bea.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBlockedByMe").value(true));
    }

    @Test
    void blockedUser_doesNotFindTheBlockerInSearchesOrFollowerLists() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        // cris follows both, so both appear in her followers lists
        follow(ana, cris);
        follow(bea, cris);

        block(ana, bea);

        mockMvc.perform(get("/api/users/search").param("query", ana.getUsername()).with(auth.bearer(bea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(not(hasItem(ana.getUsername()))));
        mockMvc.perform(get("/api/users/search").param("query", bea.getUsername()).with(auth.bearer(ana)))
                .andExpect(jsonPath("$.content[*].username").value(not(hasItem(bea.getUsername()))));
        mockMvc.perform(get("/api/users/search").param("query", bea.getUsername()).with(auth.bearer(cris)))
                .andExpect(jsonPath("$.content[*].username").value(hasItem(bea.getUsername())));

        mockMvc.perform(get("/api/users/followers/{u}", cris.getUsername()).with(auth.bearer(bea)))
                .andExpect(jsonPath("$.content[*].username").value(not(hasItem(ana.getUsername()))))
                .andExpect(jsonPath("$.content[*].username").value(hasItem(bea.getUsername())));
        // The blocker's own lists are hidden from the blocked user
        mockMvc.perform(get("/api/users/followers/{u}", ana.getUsername()).with(auth.bearer(bea)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dmPolicy_isCheckedWhenCreatingAChat_notForExistingOnes() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UserAccount dora = testData.createUser();
        UUID existing = testData.createPrivateConversation(dora, ana);

        // Default: EVERYONE (D-018)
        assertThat(openPrivateChat(bea, ana)).isEqualTo(200);

        setPolicy(ana, "NOBODY");
        assertThat(openPrivateChat(cris, ana)).isEqualTo(403);
        // The chat that already existed keeps working
        messageService.processIncomingMessage(existing, dora.getId(), "sigo aquí");

        setPolicy(ana, "FOLLOWING");
        assertThat(openPrivateChat(cris, ana)).isEqualTo(403);
        follow(ana, cris);
        assertThat(openPrivateChat(cris, ana)).isEqualTo(200);

        setPolicy(ana, "MUTUALS");
        UserAccount eva = testData.createUser();
        follow(ana, eva);
        assertThat(openPrivateChat(eva, ana)).isEqualTo(403);
        follow(eva, ana);
        assertThat(openPrivateChat(eva, ana)).isEqualTo(200);

        // She can still start chats herself whatever her policy is
        UserAccount fani = testData.createUser();
        assertThat(openPrivateChat(ana, fani)).isEqualTo(200);
    }

    @Test
    void invalidDmPolicy_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        mockMvc.perform(patch("/api/users/me").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dmPolicy\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users/me").with(auth.bearer(ana)))
                .andExpect(jsonPath("$.dmPolicy").value("EVERYONE"));
    }

    @Test
    void groups_cannotAddSomeoneWhoBlockedYouOrWhomYouBlocked_butSharedGroupsAreKept() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UserAccount dora = testData.createUser();
        UUID shared = testData.createGroup(ana, bea, cris);

        block(bea, ana);

        // Nobody is expelled and the messages keep flowing in the shared group
        messageService.processIncomingMessage(shared, ana.getId(), "en el grupo");
        messageService.processIncomingMessage(shared, bea.getId(), "sigo aquí");
        mockMvc.perform(get("/api/conversations/{id}", shared).with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.content[*].username").value(hasItem(bea.getUsername())));

        // Adding the one who blocked her fails, and nothing at all is added
        UserAccount eva = testData.createUser();
        UUID other = testData.createGroup(ana, cris);
        mockMvc.perform(post("/api/conversations/{id}/participants", other).with(auth.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("usernames", List.of(dora.getUsername(), bea.getUsername())))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/conversations/{id}", other).with(auth.bearer(ana)))
                .andExpect(jsonPath("$.participantCount").value(2));
        // Whom she blocked herself cannot be added either
        block(ana, eva);
        mockMvc.perform(post("/api/conversations/{id}/participants", other).with(auth.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("usernames", List.of(eva.getUsername())))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/conversations/{id}/participants", other).with(auth.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("usernames", List.of(dora.getUsername())))))
                .andExpect(status().isOk());
    }

    @Test
    void creatingAGroup_withSomeoneBlocked_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        block(bea, ana);

        mockMvc.perform(post("/api/conversations/group").with(auth.bearer(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("groupName", "g", "users", List.of(cris.getUsername(), bea.getUsername())))))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockEndpoints_areIdempotent_validateTheTarget_andListPaginated() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();

        block(ana, bea);
        block(ana, bea);

        mockMvc.perform(post("/api/users/{u}/block", ana.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/{u}/block", "nobody_here_" + System.nanoTime()).with(auth.bearer(ana)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/me/blocks").param("size", "1000").with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username").value(hasItem(bea.getUsername())))
                .andExpect(jsonPath("$.content.length()").value(1));
        // Only her own blocks: the blocked user's list does not show the blocker
        mockMvc.perform(get("/api/users/me/blocks").with(auth.bearer(bea)))
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(delete("/api/users/{u}/block", bea.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/users/{u}/block", bea.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/users/{u}", ana.getUsername()).with(auth.bearer(bea)))
                .andExpect(status().isOk());
    }
}
