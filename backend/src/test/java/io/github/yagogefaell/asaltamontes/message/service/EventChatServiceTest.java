package io.github.yagogefaell.asaltamontes.message.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.event.domain.EventStatus;
import io.github.yagogefaell.asaltamontes.event.dto.EventTiming;

class EventChatServiceTest {

    private static final Instant NOW = Instant.parse("2027-01-10T12:00:00Z");

    @Test
    void readOnlyReason_isNullUntilSevenDaysAfterTheEnd() {
        Instant end = NOW.minus(Duration.ofDays(7)).plus(Duration.ofMinutes(1));
        assertThat(EventChatService.readOnlyReason(new EventTiming(EventStatus.SCHEDULED, end.minus(Duration.ofHours(2)), end), NOW)).isNull();
        assertThat(EventChatService.readOnlyReason(new EventTiming(EventStatus.SCHEDULED, NOW.plus(Duration.ofDays(1)), null), NOW)).isNull();
    }

    @Test
    void readOnlyReason_isArchivedAfterwards_andCancelledWhateverTheDate() {
        Instant end = NOW.minus(Duration.ofDays(7)).minus(Duration.ofMinutes(1));
        assertThat(EventChatService.readOnlyReason(new EventTiming(EventStatus.SCHEDULED, end.minus(Duration.ofHours(2)), end), NOW))
                .isEqualTo(EventChatService.ARCHIVED);
        // Without an end time it counts as finished 12 h after the start
        assertThat(EventChatService.readOnlyReason(new EventTiming(EventStatus.SCHEDULED, NOW.minus(Duration.ofDays(8)), null), NOW))
                .isEqualTo(EventChatService.ARCHIVED);
        assertThat(EventChatService.readOnlyReason(new EventTiming(EventStatus.CANCELLED, NOW.plus(Duration.ofDays(3)), null), NOW))
                .isEqualTo(EventChatService.CANCELLED);
    }
}
