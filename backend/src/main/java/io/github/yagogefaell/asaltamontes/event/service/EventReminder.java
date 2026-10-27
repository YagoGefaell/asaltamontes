package io.github.yagogefaell.asaltamontes.event.service;

/** An event starts in about {@code hoursBefore} hours (24 or 2): everybody who is going is reminded (T21). */
public record EventReminder(Long eventId, int hoursBefore) {}
