package io.github.yagogefaell.asaltamontes.event.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.domain.EventStatus;

/**
 * iCalendar (RFC 5545) file of one event, for "add to calendar" (T21). Times go in UTC ({@code ...Z}): the instant is
 * unambiguous, and the calendar app of the person shows it in her own zone. User text is escaped, so a title or a
 * description cannot add properties to the file, and lines are folded at 75 bytes as the standard asks.
 */
final class EventCalendar {

    static final String CRLF = "\r\n";
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int MAX_LINE_BYTES = 75;

    private EventCalendar() {}

    static String ics(Event event, Instant now) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//Asaltamontes Female//Quedadas//ES");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + event.getPublicId() + "@asaltamontes");
        lines.add("DTSTAMP:" + UTC.format(now));
        lines.add("DTSTART:" + UTC.format(event.getStartsAt()));
        if (event.getEndsAt() != null) {
            lines.add("DTEND:" + UTC.format(event.getEndsAt()));
        }
        lines.add("SUMMARY:" + escape(event.getTitle()));
        String description = description(event);
        if (!description.isEmpty()) {
            lines.add("DESCRIPTION:" + escape(description));
        }
        String location = location(event);
        if (!location.isEmpty()) {
            lines.add("LOCATION:" + escape(location));
        }
        if (event.getLatitude() != null && event.getLongitude() != null) {
            lines.add(String.format(Locale.ROOT, "GEO:%.6f;%.6f", event.getLatitude(), event.getLongitude()));
        }
        lines.add("STATUS:" + (event.getStatus() == EventStatus.CANCELLED ? "CANCELLED" : "CONFIRMED"));
        // A calendar that already has the event replaces it when the file is newer
        lines.add("SEQUENCE:" + event.getVersion());
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");

        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(fold(line)).append(CRLF);
        }
        return out.toString();
    }

    private static String description(Event event) {
        StringBuilder text = new StringBuilder();
        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            text.append(event.getDescription().strip());
        }
        if (event.getDistanceKm() != null) {
            appendLine(text, event.getDistanceKm().stripTrailingZeros().toPlainString() + " km");
        }
        if (event.getElevationGainM() != null) {
            appendLine(text, "+" + event.getElevationGainM() + " m");
        }
        if (event.getStatus() == EventStatus.CANCELLED && event.getCancelReason() != null) {
            appendLine(text, "Cancelada: " + event.getCancelReason());
        }
        return text.toString();
    }

    private static void appendLine(StringBuilder text, String line) {
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(line);
    }

    private static String location(Event event) {
        List<String> parts = new ArrayList<>();
        if (event.getMeetingPointName() != null && !event.getMeetingPointName().isBlank()) {
            parts.add(event.getMeetingPointName().strip());
        }
        if (event.getCity() != null && !event.getCity().isBlank()) {
            parts.add(event.getCity().strip());
        }
        return String.join(", ", parts);
    }

    /** TEXT values: backslash, semicolon and comma are escaped, line breaks become \n and other control characters go. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case ';' -> out.append("\\;");
                case ',' -> out.append("\\,");
                case '\n' -> out.append("\\n");
                case '\r' -> {
                    // CRLF counts as one break
                    if (i + 1 >= text.length() || text.charAt(i + 1) != '\n') {
                        out.append("\\n");
                    }
                }
                default -> {
                    if (c >= ' ' && c != 0x7f) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Lines longer than 75 bytes continue on the next line after CRLF + one space; never inside a UTF-8 character. */
    static String fold(String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= MAX_LINE_BYTES) {
            return line;
        }
        StringBuilder out = new StringBuilder();
        int budget = MAX_LINE_BYTES;
        int used = 0;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            int bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (used + bytes > budget) {
                out.append(CRLF).append(' ');
                // The continuation starts with the space, which counts
                budget = MAX_LINE_BYTES - 1;
                used = 0;
            }
            out.appendCodePoint(codePoint);
            used += bytes;
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }
}
