package io.github.yagogefaell.asaltamontes.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class EventCalendarTest {

    @Test
    void escape_protectsTheSeparatorsAndRemovesLineBreaksAndControlCharacters() {
        assertThat(EventCalendar.escape("a;b,c\\d")).isEqualTo("a\\;b\\,c\\\\d");
        assertThat(EventCalendar.escape("uno\r\ndos\ntres\rcuatro")).isEqualTo("uno\\ndos\\ntres\\ncuatro");
        assertThat(EventCalendar.escape("x\u0000y\u001bz")).isEqualTo("xyz");
        // A line that tries to add a property stays inside the text
        assertThat(EventCalendar.escape("hola\r\nATTENDEE:mailto:x@y.z")).isEqualTo("hola\\nATTENDEE:mailto:x@y.z");
    }

    @Test
    void fold_keepsLinesUnder76Bytes_andNeverSplitsACharacter() {
        String line = "DESCRIPTION:" + "ñandú ".repeat(40) + "🏔️";
        String folded = EventCalendar.fold(line);

        for (String part : folded.split("\r\n")) {
            assertThat(part.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        // Unfolding gives the original back
        assertThat(folded.replace("\r\n ", "")).isEqualTo(line);
        assertThat(EventCalendar.fold("SHORT:line")).isEqualTo("SHORT:line");
    }
}
