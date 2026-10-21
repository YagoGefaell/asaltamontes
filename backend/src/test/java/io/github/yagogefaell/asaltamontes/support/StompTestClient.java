package io.github.yagogefaell.asaltamontes.support;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Real STOMP client over WebSocket for integration tests (same protocol as the frontend's @stomp/stompjs).
 */
public class StompTestClient {

    private final String url;
    private final ObjectMapper objectMapper;

    /** Uses the application's ObjectMapper, so payloads (dates…) are read exactly as the server writes them. */
    public StompTestClient(int port, ObjectMapper objectMapper) {
        this.url = "ws://localhost:" + port + "/ws-asaltamontes";
        this.objectMapper = objectMapper;
    }

    /** Connects with the given Authorization header value (null = no header). */
    public Connection connect(String authorization) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);

        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost");
        StompHeaders connectHeaders = new StompHeaders();
        if (authorization != null) {
            connectHeaders.add("Authorization", authorization);
        }

        Connection connection = new Connection();
        connection.session = client.connectAsync(url, handshakeHeaders, connectHeaders, connection.handler)
                .get(5, TimeUnit.SECONDS);
        return connection;
    }

    public static final class Connection {

        private StompSession session;
        /** Completes with the message of the first ERROR frame the server sends. */
        public final CompletableFuture<String> serverError = new CompletableFuture<>();

        private final StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                serverError.complete(headers.getFirst("message"));
            }

            @Override
            public void handleException(StompSession s, StompCommand command, StompHeaders headers, byte[] payload, Throwable ex) {
                serverError.complete(ex.getMessage());
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                serverError.complete(ex.getMessage());
            }
        };

        public <T> BlockingQueue<T> subscribe(String destination, Class<T> type) throws InterruptedException {
            BlockingQueue<T> received = new LinkedBlockingQueue<>();
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return type;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    received.add(type.cast(payload));
                }
            });
            // Give the subscription time to reach the broker before anything is sent
            Thread.sleep(300);
            return received;
        }

        public void send(String destination, Object payload) {
            session.send(destination, payload);
        }

        public void disconnect() {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }
}
