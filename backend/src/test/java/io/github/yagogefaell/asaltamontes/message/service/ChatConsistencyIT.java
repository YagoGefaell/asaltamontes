package io.github.yagogefaell.asaltamontes.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.yagogefaell.asaltamontes.message.repository.ConversationParticipantRepository;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * T09: counters under concurrency, broadcast only after commit, and conversation rules.
 */
@TestPropertySource(properties = "asaltamontes.chat.max-group-size=3")
class ChatConsistencyIT extends AbstractIntegrationTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void fiftyConcurrentMessages_leaveExactlyFiftyUnreadForEachRecipient() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        Long groupId = messageService.createGroupConversation(ana.getUsername(), "Trail", null, null,
                List.of(bea.getUsername(), cris.getUsername())).conversationId();

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Object>> sends = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int n = i;
            sends.add(() -> messageService.processIncomingMessage(groupId, ana.getId(), "mensaje " + n));
        }
        for (Future<Object> result : pool.invokeAll(sends)) {
            result.get();
        }
        pool.shutdown();

        assertThat(unreadOf(groupId, bea)).isEqualTo(50);
        assertThat(unreadOf(groupId, cris)).isEqualTo(50);
        assertThat(unreadOf(groupId, ana)).isZero();
    }

    @Test
    void aMessageWhoseTransactionRollsBack_isNeverBroadcast() {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);
        clearInvocations(messagingTemplate);

        transactionTemplate.executeWithoutResult(status -> {
            messageService.processIncomingMessage(conversationId, ana.getId(), "no debería llegar");
            status.setRollbackOnly();
        });

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void aCommittedMessage_isBroadcastToEveryParticipantById() {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        Long conversationId = testData.createPrivateConversation(ana, bea);
        clearInvocations(messagingTemplate);

        messageService.processIncomingMessage(conversationId, ana.getId(), "hola");

        verify(messagingTemplate, timeout(2000)).convertAndSendToUser(eq(String.valueOf(bea.getId())), eq("/queue/messages"), any());
        verify(messagingTemplate, timeout(2000)).convertAndSendToUser(eq(String.valueOf(ana.getId())), eq("/queue/messages"), any());
    }

    @Test
    void aPrivateChatWithYourself_isRejected() throws Exception {
        UserAccount ana = testData.createUser();

        mockMvc.perform(post("/api/conversations/private")
                        .with(auth.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ana.getUsername().toUpperCase()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void groupsLargerThanTheConfiguredMaximum_areRejected() throws Exception {
        UserAccount ana = testData.createUser();
        List<String> members = List.of(testData.createUser().getUsername(), testData.createUser().getUsername(),
                testData.createUser().getUsername());

        mockMvc.perform(post("/api/conversations/group")
                        .with(auth.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("groupName", "Demasiadas", "users", members))))
                .andExpect(status().isBadRequest());
    }

    private int unreadOf(Long conversationId, UserAccount user) {
        return transactionTemplate.execute(status -> participantRepository
                .findByConversationIdAndUserUsername(conversationId, user.getUsername())
                .orElseThrow()
                .getUnreadCount());
    }
}
