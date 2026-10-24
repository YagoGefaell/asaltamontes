package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import io.github.yagogefaell.asaltamontes.message.dto.ChatErrorDto;
import io.github.yagogefaell.asaltamontes.message.dto.MessageDto;
import io.github.yagogefaell.asaltamontes.message.dto.SendMessageCommandDto;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.StompTestClient;
import io.github.yagogefaell.asaltamontes.support.StompTestClient.Connection;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/**
 * T09: the chat over a real STOMP connection.
 */
class ChatWebSocketIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private StompTestClient stomp;

    @BeforeEach
    void setUp() {
        stomp = new StompTestClient(port, objectMapper);
    }

    @Test
    void participantMessage_isDeliveredToTheOtherParticipant() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID conversationId = testData.createPrivateConversation(ana, bea);
        Connection beaConnection = stomp.connect("Bearer " + auth.loginAs(bea));
        BlockingQueue<MessageDto> beaInbox = beaConnection.subscribe("/user/queue/messages", MessageDto.class);
        Connection anaConnection = stomp.connect("Bearer " + auth.loginAs(ana));

        anaConnection.send("/app/chat.send", new SendMessageCommandDto(conversationId, "  hola bea  "));

        MessageDto received = beaInbox.poll(5, TimeUnit.SECONDS);
        assertThat(received).as("client error: %s", beaConnection.serverError.getNow(null)).isNotNull();
        assertThat(received.content()).isEqualTo("hola bea");
        assertThat(received.senderUsername()).isEqualTo(ana.getUsername());
        anaConnection.disconnect();
        beaConnection.disconnect();
    }

    @Test
    void nonParticipant_cannotSendNorReadTheHistory() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount intruder = testData.createUser();
        UUID conversationId = testData.createPrivateConversation(ana, bea);
        Connection beaConnection = stomp.connect("Bearer " + auth.loginAs(bea));
        BlockingQueue<MessageDto> beaInbox = beaConnection.subscribe("/user/queue/messages", MessageDto.class);
        Connection intruderConnection = stomp.connect("Bearer " + auth.loginAs(intruder));
        BlockingQueue<ChatErrorDto> intruderErrors = intruderConnection.subscribe("/user/queue/errors", ChatErrorDto.class);

        intruderConnection.send("/app/chat.send", new SendMessageCommandDto(conversationId, "hola"));

        assertThat(intruderErrors.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(beaInbox.poll(1, TimeUnit.SECONDS)).isNull();
        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages").with(auth.bearer(intruder)))
                .andExpect(status().isForbidden());
        intruderConnection.disconnect();
        beaConnection.disconnect();
    }

    @Test
    void emptyOrTooLongMessages_returnAnErrorAndAreNotSaved() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID conversationId = testData.createPrivateConversation(ana, bea);
        Connection anaConnection = stomp.connect("Bearer " + auth.loginAs(ana));
        BlockingQueue<ChatErrorDto> errors = anaConnection.subscribe("/user/queue/errors", ChatErrorDto.class);

        anaConnection.send("/app/chat.send", new SendMessageCommandDto(conversationId, "   "));
        anaConnection.send("/app/chat.send", new SendMessageCommandDto(conversationId, "x".repeat(2001)));

        // Inbound frames are processed by a thread pool: the two errors can arrive in any order
        List<String> details = List.of(errors.poll(5, TimeUnit.SECONDS).detail(), errors.poll(5, TimeUnit.SECONDS).detail());
        assertThat(details).containsExactlyInAnyOrder(
                "El mensaje no puede estar vacío", "El mensaje no puede superar los 2000 caracteres");
        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages").with(auth.bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        anaConnection.disconnect();
    }

    @Test
    void connectWithoutToken_isRejected() {
        assertThatThrownBy(() -> stomp.connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connectWithAnInvalidOrRefreshToken_isRejected() {
        assertThatThrownBy(() -> stomp.connect("Bearer not.a.jwt")).isInstanceOf(ExecutionException.class);
    }

    @Test
    void subscribingOutsideTheUserQueues_isRejected() throws Exception {
        UserAccount ana = testData.createUser();
        Connection connection = stomp.connect("Bearer " + auth.loginAs(ana));

        connection.subscribe("/topic/everything", Object.class);

        assertThat(connection.serverError.get(5, TimeUnit.SECONDS)).contains("Suscripción no permitida");
    }

    @Test
    void afterChangingTheUsername_messagesStillArriveWithoutReconnecting() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UUID conversationId = testData.createPrivateConversation(ana, bea);
        Connection beaConnection = stomp.connect("Bearer " + auth.loginAs(bea));
        BlockingQueue<MessageDto> beaInbox = beaConnection.subscribe("/user/queue/messages", MessageDto.class);

        mockMvc.perform(patch("/api/users/me")
                        .with(auth.bearer(bea))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", testData.uniqueName("renamed")))))
                .andExpect(status().isOk());

        Connection anaConnection = stomp.connect("Bearer " + auth.loginAs(ana));
        anaConnection.send("/app/chat.send", new SendMessageCommandDto(conversationId, "¿me oyes?"));

        assertThat(beaInbox.poll(5, TimeUnit.SECONDS)).isNotNull();
        anaConnection.disconnect();
        beaConnection.disconnect();
    }
}
