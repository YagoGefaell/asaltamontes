package io.github.yagogefaell.asaltamontes.message.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import io.github.yagogefaell.asaltamontes.message.dto.ChatErrorDto;
import io.github.yagogefaell.asaltamontes.message.dto.SendMessageCommandDto;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

class ChatErrorsIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void sendingToAChatYouDoNotBelongTo_returnsAnErrorToTheSender() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount intruder = testData.createUser();
        UUID conversationId = testData.createPrivateConversation(ana, bea);

        StompSession session = connectAs(intruder);
        BlockingQueue<ChatErrorDto> errors = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatErrorDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add((ChatErrorDto) payload);
            }
        });
        // Let the subscription reach the broker before sending
        Thread.sleep(300);

        session.send("/app/chat.send", new SendMessageCommandDto(conversationId, "hola"));

        ChatErrorDto error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.detail()).isEqualTo("No tienes permiso para escribir en este chat");
        session.disconnect();
    }

    @Test
    void aConversationIdThatIsNotAUuid_returnsAnErrorToTheSender() throws Exception {
        UserAccount ana = testData.createUser();
        StompSession session = connectAs(ana);
        BlockingQueue<ChatErrorDto> errors = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatErrorDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add((ChatErrorDto) payload);
            }
        });
        Thread.sleep(300);

        session.send("/app/chat.send", java.util.Map.of("conversationId", "42", "content", "hola"));

        ChatErrorDto error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.detail()).isEqualTo("Mensaje no válido");
        session.disconnect();
    }

    private StompSession connectAs(UserAccount user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost");
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + auth.loginAs(user));

        return client.connectAsync("ws://localhost:" + port + "/ws-asaltamontes", handshakeHeaders, connectHeaders,
                        new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
    }
}
