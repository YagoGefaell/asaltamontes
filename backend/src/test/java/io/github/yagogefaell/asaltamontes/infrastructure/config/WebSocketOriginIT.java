package io.github.yagogefaell.asaltamontes.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;

class WebSocketOriginIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void handshake_fromAllowedOrigin_isAccepted() throws Exception {
        WebSocketSession session = connectFrom("http://localhost");

        assertThat(session.isOpen()).isTrue();
        session.close();
    }

    @Test
    void handshake_fromOriginNotInCorsList_isRejected() {
        assertThatThrownBy(() -> connectFrom("http://evil.example"))
                .isInstanceOf(ExecutionException.class);
    }

    private WebSocketSession connectFrom(String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        URI uri = URI.create("ws://localhost:" + port + "/ws-asaltamontes");

        return new StandardWebSocketClient()
                .execute(new TextWebSocketHandler(), headers, uri)
                .get(5, TimeUnit.SECONDS);
    }
}
