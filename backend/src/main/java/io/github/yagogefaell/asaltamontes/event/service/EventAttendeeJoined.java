package io.github.yagogefaell.asaltamontes.event.service;

/** Someone joined an event (or its waitlist); the organizer is told (T18). Internal ids: never leaves the backend. */
public record EventAttendeeJoined(Long eventId, Long userId, boolean waitlisted) {}
