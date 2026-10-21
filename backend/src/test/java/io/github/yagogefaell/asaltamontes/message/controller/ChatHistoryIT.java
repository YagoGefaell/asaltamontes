package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessagePageDto;
import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * T10: chat history by cursor.
 */
class ChatHistoryIT extends AbstractIntegrationTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void consecutivePages_coverTheWholeHistoryOnceInOrder() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);
        for (int i = 1; i <= 75; i++) {
            messageService.processIncomingMessage(conversationId, ana.getId(), "mensaje " + i);
        }

        List<String> all = new ArrayList<>();
        MessagePageDto page = fetch(history(conversationId).param("limit", "30"), bea);
        assertThat(page.items()).hasSize(30);
        assertThat(page.items().get(29).content()).isEqualTo("mensaje 75");
        all.addAll(0, page.items().stream().map(MessageDto::content).toList());

        while (page.hasMore()) {
            page = fetch(history(conversationId).param("limit", "30").param("before", page.nextBefore().toString()), bea);
            all.addAll(0, page.items().stream().map(MessageDto::content).toList());
        }

        assertThat(page.nextBefore()).isNull();
        assertThat(all).hasSize(75).doesNotHaveDuplicates();
        assertThat(all.get(0)).isEqualTo("mensaje 1");
        assertThat(all.get(74)).isEqualTo("mensaje 75");
    }

    @Test
    void defaultPageIs30AndTheLimitIsCappedAt100() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);
        for (int i = 0; i < 120; i++) {
            messageService.processIncomingMessage(conversationId, ana.getId(), "m" + i);
        }

        assertThat(fetch(history(conversationId), ana).items()).hasSize(30);
        MessagePageDto capped = fetch(history(conversationId).param("limit", "1000"), ana);
        assertThat(capped.items()).hasSize(100);
        assertThat(capped.hasMore()).isTrue();
    }

    @Test
    void anEmptyChat_returnsNoItemsAndNoMorePages() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);

        MessagePageDto page = fetch(history(conversationId), ana);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void nonParticipant_isForbidden() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount outsider = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);

        mockMvc.perform(history(conversationId).with(auth.bearer(outsider))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/conversations/" + conversationId + "/read").with(auth.bearer(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void readingTheHistoryNoLongerMarksAsRead_theReadEndpointDoes() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);
        messageService.processIncomingMessage(conversationId, ana.getId(), "hola");

        fetch(history(conversationId), bea);
        assertThat(unreadOf(conversationId, bea)).isEqualTo(1);

        mockMvc.perform(post("/api/conversations/" + conversationId + "/read").with(auth.bearer(bea)))
                .andExpect(status().isNoContent());
        assertThat(unreadOf(conversationId, bea)).isZero();
    }

    @Test
    void after_returnsOnlyNewerMessagesOldestFirst_inPages() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);
        Long lastSeen = null;
        for (int i = 1; i <= 25; i++) {
            Long id = messageService.processIncomingMessage(conversationId, ana.getId(), "mensaje " + i).id();
            if (i == 5) {
                lastSeen = id;
            }
        }

        MessagePageDto first = fetch(history(conversationId).param("after", lastSeen.toString()).param("limit", "15"), bea);
        assertThat(first.items()).extracting(MessageDto::content).first().isEqualTo("mensaje 6");
        assertThat(first.items()).hasSize(15);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextBefore()).isNull();

        Long lastOfFirst = first.items().get(14).id();
        MessagePageDto second = fetch(history(conversationId).param("after", lastOfFirst.toString()).param("limit", "15"), bea);
        assertThat(second.items()).extracting(MessageDto::content).containsExactly(
                "mensaje 21", "mensaje 22", "mensaje 23", "mensaje 24", "mensaje 25");
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void after_isForbiddenForNonParticipants_andCannotBeCombinedWithBefore() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount outsider = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);

        mockMvc.perform(history(conversationId).param("after", "0").with(auth.bearer(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(history(conversationId).param("after", "1").param("before", "10").with(auth.bearer(ana)))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder history(Long conversationId) {
        return get("/api/conversations/" + conversationId + "/messages");
    }

    private MessagePageDto fetch(MockHttpServletRequestBuilder request, UserAccount user) throws Exception {
        String body = mockMvc.perform(request.with(auth.bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, MessagePageDto.class);
    }

    private int unreadOf(Long conversationId, UserAccount user) {
        return transactionTemplate.execute(status -> participantRepository
                .findByConversationIdAndUserUsername(conversationId, user.getUsername())
                .orElseThrow()
                .getUnreadCount());
    }
}
