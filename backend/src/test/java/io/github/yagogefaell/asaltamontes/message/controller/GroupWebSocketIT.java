package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import io.github.yagogefaell.asaltamontes.message.domain.MessageType;
import io.github.yagogefaell.asaltamontes.message.dto.ConversationRemovedDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.StompTestClient;
import io.github.yagogefaell.asaltamontes.support.StompTestClient.Connection;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T12: what the members' WebSocket sessions receive when the group changes. */
@TestPropertySource(properties = "asaltamontes.chat.max-group-size=3")
class GroupWebSocketIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private MessageService messageService;

    private StompTestClient stomp;

    @BeforeEach
    void setUp() {
        stomp = new StompTestClient(port, objectMapper);
    }

    @Test
    void anExpelledMember_getsRemovedAndNoMoreMessages_andTheOthersGetTheSystemMessage() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID group = testData.createGroup(ana, bea, cris);
        Connection beaConnection = stomp.connect("Bearer " + auth.loginAs(bea));
        BlockingQueue<MessageDto> beaMessages = beaConnection.subscribe("/user/queue/messages", MessageDto.class);
        BlockingQueue<ConversationRemovedDto> beaRemoved = beaConnection.subscribe("/user/queue/conversations", ConversationRemovedDto.class);
        Connection crisConnection = stomp.connect("Bearer " + auth.loginAs(cris));
        BlockingQueue<MessageDto> crisMessages = crisConnection.subscribe("/user/queue/messages", MessageDto.class);

        mockMvc.perform(delete("/api/conversations/" + group + "/participants/" + bea.getUsername()).with(auth.bearer(ana)))
                .andExpect(status().isNoContent());

        ConversationRemovedDto removed = beaRemoved.poll(5, TimeUnit.SECONDS);
        assertThat(removed).as("client error: %s", beaConnection.serverError.getNow(null)).isNotNull();
        assertThat(removed.conversationId()).isEqualTo(group);
        assertThat(removed.reason()).isEqualTo("REMOVED");
        MessageDto system = crisMessages.poll(5, TimeUnit.SECONDS);
        assertThat(system).isNotNull();
        assertThat(system.type()).isEqualTo(MessageType.SYSTEM);
        assertThat(system.senderUsername()).isNull();
        assertThat(system.systemPayload()).containsEntry("action", "MEMBER_REMOVED");

        messageService.processIncomingMessage(group, ana.getId(), "ya no estás");
        assertThat(crisMessages.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(beaMessages.poll(1, TimeUnit.SECONDS)).isNull();
        beaConnection.disconnect();
        crisConnection.disconnect();
    }

    @Test
    void whoLeaves_isToldInHerSessions() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID group = testData.createGroup(ana, bea);
        Connection beaConnection = stomp.connect("Bearer " + auth.loginAs(bea));
        BlockingQueue<ConversationRemovedDto> removed = beaConnection.subscribe("/user/queue/conversations", ConversationRemovedDto.class);

        mockMvc.perform(post("/api/conversations/" + group + "/leave").with(auth.bearer(bea))).andExpect(status().isNoContent());

        ConversationRemovedDto event = removed.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.reason()).isEqualTo("LEFT");
        beaConnection.disconnect();
    }
}
