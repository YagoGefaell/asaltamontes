package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.yagogefaell.asaltamontes.message.dto.ChatPageDto;
import io.github.yagogefaell.asaltamontes.message.dto.ChatSummaryDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * T10b: chat list by cursor.
 */
class ChatListIT extends AbstractIntegrationTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pagesCoverEveryChatOnce_recentFirst_tiesById_andChatsWithoutMessagesLast() throws Exception {
        UserAccount ana = testData.createUser();
        List<UUID> withMessages = new ArrayList<>();
        List<UUID> withoutMessages = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            UUID id = testData.createPrivateConversation(ana, testData.createUser());
            messageService.processIncomingMessage(id, ana.getId(), "hola " + i);
            withMessages.add(id);
        }
        for (int i = 0; i < 7; i++) {
            withoutMessages.add(testData.createPrivateConversation(ana, testData.createUser()));
        }
        // Four chats with exactly the same time: the id breaks the tie
        List<UUID> tied = withMessages.subList(5, 9);
        jdbcTemplate.update("UPDATE conversations SET last_message_at = (SELECT last_message_at FROM conversations WHERE public_id = ?)"
                + " WHERE public_id IN (" + String.join(",", tied.stream().map(id -> "'" + id + "'").toList()) + ")", tied.get(0));

        List<UUID> seen = new ArrayList<>();
        ChatPageDto page = fetch(chats().param("limit", "4"), ana);
        seen.addAll(page.items().stream().map(ChatSummaryDto::conversationId).toList());
        while (page.hasMore()) {
            MockHttpServletRequestBuilder next = chats().param("limit", "4").param("beforeId", page.nextBeforeId().toString());
            if (page.nextBeforeAt() != null) {
                next.param("beforeAt", page.nextBeforeAt().toString());
            }
            page = fetch(next, ana);
            seen.addAll(page.items().stream().map(ChatSummaryDto::conversationId).toList());
        }

        assertThat(seen).hasSize(22).doesNotHaveDuplicates();
        // Without messages: last, newest id first
        assertThat(seen.subList(15, 22)).containsExactlyElementsOf(withoutMessages.reversed());
        // With messages: the latest one first; tied ones by id DESC
        assertThat(seen.get(0)).isEqualTo(withMessages.get(14));
        assertThat(seen.indexOf(tied.get(3))).isLessThan(seen.indexOf(tied.get(2)));
        assertThat(seen.indexOf(tied.get(2))).isLessThan(seen.indexOf(tied.get(1)));
        assertThat(seen.indexOf(tied.get(1))).isLessThan(seen.indexOf(tied.get(0)));
    }

    @Test
    void unreadChats_countsChatsOutsideThePageToo() throws Exception {
        UserAccount ana = testData.createUser();
        for (int i = 0; i < 5; i++) {
            UserAccount other = testData.createUser();
            UUID id = testData.createPrivateConversation(ana, other);
            messageService.processIncomingMessage(id, other.getId(), "¿vienes el domingo?");
            messageService.processIncomingMessage(id, other.getId(), "salimos a las 8");
        }

        ChatPageDto page = fetch(chats().param("limit", "2"), ana);

        assertThat(page.items()).hasSize(2);
        assertThat(page.unreadChats()).isEqualTo(5);
    }

    @Test
    void defaultPageIs30_andBeforeAtWithoutBeforeIdIsRejected() throws Exception {
        UserAccount ana = testData.createUser();
        for (int i = 0; i < 31; i++) {
            testData.createPrivateConversation(ana, testData.createUser());
        }

        ChatPageDto page = fetch(chats(), ana);
        assertThat(page.items()).hasSize(30);
        assertThat(page.hasMore()).isTrue();

        mockMvc.perform(chats().param("beforeAt", "2026-10-21T20:00:00").with(auth.bearer(ana)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manipulatedCursor_isRejectedWith400_includingAChatOfSomeoneElse() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        testData.createPrivateConversation(ana, testData.createUser());
        UUID foreign = testData.createPrivateConversation(bea, testData.createUser());

        for (String cursor : List.of("7", "abc", UUID.randomUUID().toString(), foreign.toString())) {
            mockMvc.perform(chats().param("beforeId", cursor).with(auth.bearer(ana))).andExpect(status().isBadRequest());
        }
    }

    private static MockHttpServletRequestBuilder chats() {
        return get("/api/conversations");
    }

    private ChatPageDto fetch(MockHttpServletRequestBuilder request, UserAccount user) throws Exception {
        String body = mockMvc.perform(request.with(auth.bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, ChatPageDto.class);
    }
}
