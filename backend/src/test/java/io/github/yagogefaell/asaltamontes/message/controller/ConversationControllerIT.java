package io.github.yagogefaell.asaltamontes.message.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class ConversationControllerIT extends AbstractIntegrationTest {

    @Test
    void getMyConversations_listsOnlyConversationsWhereUserParticipates() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount outsider = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);

        mockMvc.perform(get("/api/conversations").with(auth.bearer(bea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].conversationId").value(hasItem(conversationId.intValue())));

        mockMvc.perform(get("/api/conversations").with(auth.bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].conversationId").value(not(hasItem(conversationId.intValue()))));
    }
}
