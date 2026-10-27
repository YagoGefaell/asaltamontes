package io.github.yagogefaell.asaltamontes.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;

class OpaqueCursorTest {

    @Test
    void roundTrip_keepsOrderAtMicrosecondPrecisionAndThePublicId() {
        UUID id = UUID.randomUUID();
        LocalDateTime at = LocalDateTime.of(2026, 10, 26, 12, 30, 15, 123_456_789);

        OpaqueCursor decoded = OpaqueCursor.decode(OpaqueCursor.encode(OpaqueCursor.MESSAGE, at, id), OpaqueCursor.MESSAGE);

        assertThat(decoded.publicId()).isEqualTo(id);
        assertThat(decoded.order()).isEqualTo(at.withNano(123_456_000));
    }

    @Test
    void aMissingOrderSurvivesTheRoundTrip_andBlankMeansNoCursor() {
        UUID id = UUID.randomUUID();

        assertThat(OpaqueCursor.decode(OpaqueCursor.encode(OpaqueCursor.CHAT, null, id), OpaqueCursor.CHAT).order()).isNull();
        assertThat(OpaqueCursor.decode(null, OpaqueCursor.CHAT)).isNull();
        assertThat(OpaqueCursor.decode("  ", OpaqueCursor.CHAT)).isNull();
    }

    @Test
    void theTokenIsOpaque_urlSafeAndNeverContainsThePublicIdInTheClear() {
        UUID id = UUID.randomUUID();

        String token = OpaqueCursor.encode(OpaqueCursor.MESSAGE, LocalDateTime.now(), id);

        assertThat(token).matches("[A-Za-z0-9_-]+").doesNotContain(id.toString());
    }

    @Test
    void invalidTokensAreBadRequests() {
        UUID id = UUID.randomUUID();
        String chat = OpaqueCursor.encode(OpaqueCursor.CHAT, LocalDateTime.now(), id);
        String notCanonical = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("m|0001|" + id).getBytes(StandardCharsets.UTF_8));
        String badUuid = Base64.getUrlEncoder().withoutPadding().encodeToString("m||nope".getBytes(StandardCharsets.UTF_8));
        String tooMany = Base64.getUrlEncoder().withoutPadding().encodeToString(("m||" + id + "|x").getBytes(StandardCharsets.UTF_8));

        String minMicros = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("m|" + Long.MIN_VALUE + "|" + id).getBytes(StandardCharsets.UTF_8));
        String maxMicros = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("m|" + Long.MAX_VALUE + "|" + id).getBytes(StandardCharsets.UTF_8));

        for (String token : new String[] {"5", "!!!", id.toString(), chat, notCanonical, badUuid, tooMany, chat + "=",
                minMicros, maxMicros}) {
            assertThatThrownBy(() -> OpaqueCursor.decode(token, OpaqueCursor.MESSAGE))
                    .as(token).isInstanceOf(BadRequestException.class);
        }
    }
}
