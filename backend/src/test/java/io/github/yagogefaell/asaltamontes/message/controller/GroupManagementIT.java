package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationRepository;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * T12: management of group chats (permissions, members, roles, system messages, visible history, concurrency).
 */
@TestPropertySource(properties = "asaltamontes.chat.max-group-size=3")
class GroupManagementIT extends AbstractIntegrationTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // --- details ---

    @Test
    void details_showTheGroupToItsMembersWithAdminsFirstAndMyRole() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);

        mockMvc.perform(get("/api/conversations/" + group).with(auth.bearer(bea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(group.toString()))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.historyVisibleToNew").value(false))
                .andExpect(jsonPath("$.participants.content[0].username").value(ana.getUsername()))
                .andExpect(jsonPath("$.participants.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.participants.content[1].role").value("MEMBER"));
    }

    @Test
    void details_participantPageSizeIsCappedAt50() throws Exception {
        UserAccount ana = testData.createUser();
        UUID group = testData.createGroup(ana, testData.createUser());

        mockMvc.perform(get("/api/conversations/" + group + "?size=500").with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.size").value(50));
    }

    @Test
    void foreignUnknownAndPrivateChatsAreTheSame404ForEveryEndpoint() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount outsider = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        UUID privateChat = testData.createPrivateConversation(ana, bea);
        String body = json(Map.of("usernames", List.of(outsider.getUsername())));

        for (UUID id : List.of(group, privateChat, UUID.randomUUID())) {
            UserAccount caller = id.equals(privateChat) ? ana : outsider;
            String detail = "$.detail";
            mockMvc.perform(get("/api/conversations/" + id).with(auth.bearer(caller)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath(detail).value("Chat no encontrado"));
            mockMvc.perform(json(patch("/api/conversations/" + id), "{\"groupName\":\"x\"}", caller))
                    .andExpect(status().isNotFound());
            mockMvc.perform(json(post("/api/conversations/" + id + "/participants"), body, caller))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/conversations/" + id + "/participants/" + bea.getUsername()).with(auth.bearer(caller)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(json(patch("/api/conversations/" + id + "/participants/" + bea.getUsername()), "{\"role\":\"ADMIN\"}", caller))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/conversations/" + id + "/leave").with(auth.bearer(caller)))
                    .andExpect(status().isNotFound());
        }
    }

    // --- permissions ---

    @Test
    void aMemberWhoIsNotAdmin_cannotManageTheGroup_butCanLeave() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea);

        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"groupName\":\"nuevo\"}", bea)).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                json(Map.of("usernames", List.of(cris.getUsername()))), bea)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/conversations/" + group + "/participants/" + ana.getUsername()).with(auth.bearer(bea)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(patch("/api/conversations/" + group + "/participants/" + bea.getUsername()), "{\"role\":\"ADMIN\"}", bea))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(bea))).andExpect(status().isNoContent());
    }

    // --- add ---

    @Test
    void addingParticipants_ignoresDuplicatesAndExistingMembers_andLeavesASystemMessage() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea);

        mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                        json(Map.of("usernames", List.of(cris.getUsername().toUpperCase(), cris.getUsername(), bea.getUsername(), ana.getUsername()))), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(3));

        JsonNode last = history(group, ana).get("items").get(0);
        assertThat(last.get("type").asText()).isEqualTo("SYSTEM");
        assertThat(last.get("senderUsername").isNull()).isTrue();
        assertThat(last.get("systemPayload").get("action").asText()).isEqualTo("MEMBER_ADDED");
        assertThat(last.get("systemPayload").get("actor").asText()).isEqualTo(ana.getUsername());
        assertThat(last.get("systemPayload").get("targets").get(0).asText()).isEqualTo(cris.getUsername());

        // The chat list shows the system message as the last one, without a sender
        mockMvc.perform(get("/api/conversations").with(auth.bearer(bea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastMessageSenderName").doesNotExist())
                .andExpect(jsonPath("$.items[0].unreadCount").value(1));
    }

    @Test
    void addingAnUnknownUsername_addsNobody() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea);

        mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                        json(Map.of("usernames", List.of(cris.getUsername(), "no_existe_nadie"))), ana))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/conversations/" + group + "/participants"), json(Map.of("usernames", List.of())), ana))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                        json(Map.of("usernames", List.of(ana.getUsername()))), ana))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/conversations/" + group).with(auth.bearer(ana)))
                .andExpect(jsonPath("$.participantCount").value(2));
    }

    @Test
    void addingBeyondTheMaximumSize_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        UUID group = testData.createGroup(ana, testData.createUser(), testData.createUser());

        mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                        json(Map.of("usernames", List.of(testData.createUser().getUsername()))), ana))
                .andExpect(status().isBadRequest());
    }

    @Test
    void twoAdminsAddingAtTheSameTime_neverExceedTheMaximumSize() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        mockMvc.perform(json(patch("/api/conversations/" + group + "/participants/" + bea.getUsername()), "{\"role\":\"ADMIN\"}", ana))
                .andExpect(status().isNoContent());
        UserAccount cris = testData.createUser();
        UserAccount dani = testData.createUser();

        List<Integer> statuses = runConcurrently(
                () -> mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                        json(Map.of("usernames", List.of(cris.getUsername()))), ana)).andReturn().getResponse().getStatus(),
                () -> mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                        json(Map.of("usernames", List.of(dani.getUsername()))), bea)).andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        assertThat(count(group)).isEqualTo(3);
    }

    // --- expel / roles ---

    @Test
    void expellingAMember_removesHerAccess_andLeavesASystemMessage() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);

        mockMvc.perform(delete("/api/conversations/" + group + "/participants/" + bea.getUsername().toUpperCase()).with(auth.bearer(ana)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/conversations/" + group + "/messages").with(auth.bearer(bea))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/conversations/" + group + "/read").with(auth.bearer(bea))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/conversations").with(auth.bearer(bea)))
                .andExpect(jsonPath("$.items.length()").value(0));
        JsonNode last = history(group, ana).get("items").get(0);
        assertThat(last.get("systemPayload").get("action").asText()).isEqualTo("MEMBER_REMOVED");
        assertThat(last.get("systemPayload").get("targets").get(0).asText()).isEqualTo(bea.getUsername());
    }

    @Test
    void expelling_aNonParticipantIs404_andYourselfIs400() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount outsider = testData.createUser();
        UUID group = testData.createGroup(ana, testData.createUser());

        mockMvc.perform(delete("/api/conversations/" + group + "/participants/" + outsider.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Participante no encontrada"));
        mockMvc.perform(delete("/api/conversations/" + group + "/participants/" + ana.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingRoles_promotesAndDemotes_butTheGroupKeepsOneAdmin() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);

        mockMvc.perform(json(patch("/api/conversations/" + group + "/participants/" + ana.getUsername()), "{\"role\":\"MEMBER\"}", ana))
                .andExpect(status().isConflict());
        mockMvc.perform(json(patch("/api/conversations/" + group + "/participants/" + bea.getUsername()), "{\"role\":\"ADMIN\"}", ana))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/conversations/" + group).with(auth.bearer(bea))).andExpect(jsonPath("$.myRole").value("ADMIN"));
        mockMvc.perform(json(patch("/api/conversations/" + group + "/participants/" + ana.getUsername()), "{\"role\":\"MEMBER\"}", bea))
                .andExpect(status().isNoContent());
        mockMvc.perform(json(patch("/api/conversations/" + group + "/participants/" + bea.getUsername()), "{\"role\":\"BOSS\"}", bea))
                .andExpect(status().isBadRequest());

        JsonNode last = history(group, bea).get("items").get(1);
        assertThat(last.get("systemPayload").get("action").asText()).isEqualTo("ROLE_CHANGED");
        assertThat(last.get("systemPayload").get("role").asText()).isEqualTo("MEMBER");
    }

    // --- leave ---

    @Test
    void leaving_asAMember_removesHerAndTellsTheOthers() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);

        mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(bea))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/conversations/" + group + "/messages").with(auth.bearer(bea))).andExpect(status().isNotFound());
        assertThat(history(group, ana).get("items").get(0).get("systemPayload").get("action").asText()).isEqualTo("MEMBER_LEFT");
    }

    @Test
    void theLastAdminLeaving_promotesTheOldestMember() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea, cris);

        mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(ana))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/conversations/" + group).with(auth.bearer(cris)))
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
        mockMvc.perform(get("/api/conversations/" + group).with(auth.bearer(bea)))
                .andExpect(jsonPath("$.myRole").value("ADMIN"));
        assertThat(history(group, bea).get("items").get(1).get("systemPayload").get("action").asText()).isEqualTo("ADMIN_ASSIGNED");
    }

    @Test
    void theLastParticipantLeaving_deletesTheConversationWithItsMessages() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        messageService.processIncomingMessage(group, ana.getId(), "hola");
        mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(bea))).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(ana))).andExpect(status().isNoContent());

        assertThat(conversationRepository.findIdByPublicId(group)).isEmpty();
    }

    @Test
    void twoAdminsLeavingAtTheSameTime_doNotLeaveTheGroupWithoutAdmin() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea, cris);
        mockMvc.perform(json(patch("/api/conversations/" + group + "/participants/" + bea.getUsername()), "{\"role\":\"ADMIN\"}", ana))
                .andExpect(status().isNoContent());

        runConcurrently(
                () -> mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(ana))).andReturn().getResponse().getStatus(),
                () -> mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(bea))).andReturn().getResponse().getStatus());

        mockMvc.perform(get("/api/conversations/" + group).with(auth.bearer(cris)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(1))
                .andExpect(jsonPath("$.myRole").value("ADMIN"));
    }

    // --- PATCH ---

    @Test
    void patch_absentFieldsChangeNothing_andNullNameIsRejected() throws Exception {
        UserAccount ana = testData.createUser();
        UUID group = testData.createGroup(ana, testData.createUser());
        int before = history(group, ana).get("items").size();

        mockMvc.perform(json(patch("/api/conversations/" + group), "{}", ana)).andExpect(status().isOk());
        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"groupName\":null}", ana)).andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"groupName\":\"   \"}", ana)).andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"groupName\":\"" + "x".repeat(81) + "\"}", ana))
                .andExpect(status().isBadRequest());

        assertThat(history(group, ana).get("items").size()).isEqualTo(before);
    }

    @Test
    void patch_renamesTheGroup_andAnExplicitNullRemovesTheImage() throws Exception {
        UserAccount ana = testData.createUser();
        UUID group = testData.createGroup(ana, testData.createUser());
        transactionTemplate.executeWithoutResult(status -> {
            var conversation = conversationRepository.findByPublicId(group).orElseThrow();
            conversation.setGroupImage("https://example.com/old.png");
            conversationRepository.save(conversation);
        });

        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"groupName\":\"  Nuevo nombre \"}", ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nuevo nombre"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/old.png"));
        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"groupImage\":null}", ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nuevo nombre"))
                .andExpect(jsonPath("$.imageUrl").doesNotExist());

        mockMvc.perform(get("/api/conversations").with(auth.bearer(ana)))
                .andExpect(jsonPath("$.items[0].displayName").value("Nuevo nombre"));
        assertThat(history(group, ana).get("items").get(0).get("systemPayload").get("action").asText()).isEqualTo("GROUP_RENAMED");
        assertThat(history(group, ana).get("items").get(1).get("systemPayload").get("action").asText()).isEqualTo("GROUP_IMAGE_CHANGED");
        // The renamed group still has the last message of the later change (nothing overwritten behind the entity)
        mockMvc.perform(get("/api/conversations").with(auth.bearer(ana)))
                .andExpect(jsonPath("$.items[0].lastMessage").value(org.hamcrest.Matchers.containsString("quitó")));
    }

    @Test
    void patch_aNewExternalImageUrlIsRejected() throws Exception {
        UserAccount ana = testData.createUser();
        UUID group = testData.createGroup(ana, testData.createUser());

        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"groupImage\":\"https://evil.example/x.png\"}", ana))
                .andExpect(status().isBadRequest());
    }

    // --- history for people who join later (Yago, 2026-10-27) ---

    @Test
    void aNewMember_seesOnlyWhatWasSentSinceSheJoined() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        messageService.processIncomingMessage(group, ana.getId(), "antes");
        addMember(group, ana, cris);
        messageService.processIncomingMessage(group, ana.getId(), "después");

        JsonNode items = history(group, cris).get("items");
        assertThat(contents(items)).containsExactly(contentOfAddedMessage(items), "después");
        assertThat(contents(history(group, bea).get("items"))).contains("antes", "después");
        // The latest page, going back by cursor, and catching up after a message: nothing before the join
        String cursor = history(group, cris).get("items").get(0).get("cursor").asText();
        mockMvc.perform(get("/api/conversations/" + group + "/messages?before=" + cursor).with(auth.bearer(cris)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        String firstOfBea = history(group, bea).get("items").get(0).get("cursor").asText();
        mockMvc.perform(get("/api/conversations/" + group + "/messages?after=" + firstOfBea).with(auth.bearer(cris)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void withTheOptionOn_aNewMemberSeesTheWholeHistory() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        messageService.processIncomingMessage(group, ana.getId(), "antes");
        mockMvc.perform(json(patch("/api/conversations/" + group), "{\"historyVisibleToNew\":true}", ana))
                .andExpect(status().isOk()).andExpect(jsonPath("$.historyVisibleToNew").value(true));
        addMember(group, ana, cris);

        assertThat(contents(history(group, cris).get("items"))).contains("antes");
    }

    @Test
    void aReadmittedMember_seesOnlyFromHerReadmission() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        messageService.processIncomingMessage(group, ana.getId(), "primera etapa");
        mockMvc.perform(delete("/api/conversations/" + group + "/participants/" + bea.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isNoContent());
        messageService.processIncomingMessage(group, ana.getId(), "mientras estaba fuera");
        addMember(group, ana, bea);
        messageService.processIncomingMessage(group, ana.getId(), "de vuelta");

        List<String> seen = contents(history(group, bea).get("items"));
        assertThat(seen).doesNotContain("primera etapa", "mientras estaba fuera").contains("de vuelta");
    }

    @Test
    void privateChatsShowTheWholeHistory() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID chat = testData.createPrivateConversation(ana, bea);
        messageService.processIncomingMessage(chat, ana.getId(), "hola");

        assertThat(contents(history(chat, bea).get("items"))).containsExactly("hola");
    }

    // --- helpers ---

    private void addMember(UUID group, UserAccount admin, UserAccount member) throws Exception {
        mockMvc.perform(json(post("/api/conversations/" + group + "/participants"),
                json(Map.of("usernames", List.of(member.getUsername()))), admin)).andExpect(status().isOk());
    }

    private JsonNode history(UUID conversation, UserAccount user) throws Exception {
        String body = mockMvc.perform(get("/api/conversations/" + conversation + "/messages?limit=50").with(auth.bearer(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static List<String> contents(JsonNode items) {
        return java.util.stream.StreamSupport.stream(items.spliterator(), false).map(n -> n.get("content").asText()).toList();
    }

    /** The "X added Y" message is the first thing a new member sees. */
    private static String contentOfAddedMessage(JsonNode items) {
        return items.get(0).get("content").asText();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body, UserAccount user) {
        return request.with(auth.bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int count(UUID group) {
        Long id = conversationRepository.findIdByPublicId(group).orElseThrow();
        return (int) participantRepository.countByConversationId(id);
    }

    @SafeVarargs
    private static List<Integer> runConcurrently(Callable<Integer>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Integer> results = new java.util.ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(List.of(tasks))) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
