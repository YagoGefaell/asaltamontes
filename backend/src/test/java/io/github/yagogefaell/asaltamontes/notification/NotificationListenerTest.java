package io.github.yagogefaell.asaltamontes.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import io.github.yagogefaell.asaltamontes.event.service.EventAttendeeJoined;
import io.github.yagogefaell.asaltamontes.event.service.EventNotificationSource;
import io.github.yagogefaell.asaltamontes.event.service.EventNotificationSource.EventInfo;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationListener;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationService;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationService.NewNotification;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.user.service.UserFollowed;

class NotificationListenerTest {

    private final NotificationService service = mock(NotificationService.class);
    private final EventNotificationSource source = mock(EventNotificationSource.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final NotificationListener listener = new NotificationListener(service, source, users, messaging);

    @Test
    void aFailureWhileCreatingIsSwallowed_neverPropagatedToTheAction() {
        when(users.findById(1L)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.onUserFollowed(new UserFollowed(1L, 2L))).doesNotThrowAnyException();
        verify(messaging, never()).convertAndSendToUser(any(), any(), any(Object.class));
    }

    @Test
    void aFailureWhileSending_isSwallowedToo() {
        when(source.find(5L)).thenReturn(Optional.of(new EventInfo(UUID.randomUUID(), "Trail", Instant.now(), "Asia/Tokyo", 9L)));
        when(service.create(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> listener.onEventAttendeeJoined(new EventAttendeeJoined(5L, 3L, false))).doesNotThrowAnyException();
    }

    @Test
    void eventPayload_carriesTitle_utcInstant_andTheEventsIanaZone() {
        Instant start = Instant.parse("2026-11-01T22:30:00Z");
        UUID id = UUID.randomUUID();
        when(source.find(5L)).thenReturn(Optional.of(new EventInfo(id, "Trail nocturno", start, "Pacific/Auckland", 9L)));
        when(service.create(any(), any())).thenReturn(List.of());

        listener.onEventAttendeeJoined(new EventAttendeeJoined(5L, 3L, true));

        var captor = org.mockito.ArgumentCaptor.forClass(NewNotification.class);
        verify(service).create(captor.capture(), org.mockito.ArgumentMatchers.eq(List.of(9L)));
        assertThat(captor.getValue().payload())
                .containsEntry("title", "Trail nocturno")
                .containsEntry("startsAt", "2026-11-01T22:30:00Z")
                .containsEntry("timeZone", "Pacific/Auckland")
                .containsEntry("waitlisted", true);
        assertThat(captor.getValue().targetRef()).isEqualTo(id.toString());
        assertThat(captor.getValue().actorId()).isEqualTo(3L);
    }
}
