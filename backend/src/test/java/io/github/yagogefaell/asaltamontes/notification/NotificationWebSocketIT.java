package io.github.yagogefaell.asaltamontes.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.yagogefaell.asaltamontes.notification.domain.NotificationType;
import io.github.yagogefaell.asaltamontes.notification.dto.NotificationDto;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.support.StompTestClient;
import io.github.yagogefaell.asaltamontes.support.StompTestClient.Connection;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

/** T18a: the recipient receives her notification live, and nobody else does. */
class NotificationWebSocketIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void theRecipient_receivesItOnHerQueue_andAnotherConnectedUserDoesNot() throws Exception {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        StompTestClient stomp = new StompTestClient(port, objectMapper);
        Connection beaConnection = stomp.connect("Bearer " + auth.loginAs(bea));
        BlockingQueue<NotificationDto> beaQueue = beaConnection.subscribe("/user/queue/notifications", NotificationDto.class);
        Connection crisConnection = stomp.connect("Bearer " + auth.loginAs(cris));
        BlockingQueue<NotificationDto> crisQueue = crisConnection.subscribe("/user/queue/notifications", NotificationDto.class);

        mockMvc.perform(post("/api/users/follow/{u}", bea.getUsername()).with(auth.bearer(ana))).andExpect(status().isOk());

        NotificationDto received = beaQueue.poll(5, TimeUnit.SECONDS);
        assertThat(received).as("client error: %s", beaConnection.serverError.getNow(null)).isNotNull();
        assertThat(received.type()).isEqualTo(NotificationType.NEW_FOLLOWER);
        assertThat(received.actor().username()).isEqualTo(ana.getUsername());
        assertThat(received.id()).isNotNull();
        assertThat(received.readAt()).isNull();
        assertThat(crisQueue.poll(1, TimeUnit.SECONDS)).isNull();
        beaConnection.disconnect();
        crisConnection.disconnect();
    }
}
