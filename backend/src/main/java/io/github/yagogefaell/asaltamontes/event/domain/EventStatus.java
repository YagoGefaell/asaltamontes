package io.github.yagogefaell.asaltamontes.event.domain;

/** Status of an event. Only SCHEDULED and CANCELLED are stored; FINISHED is derived from the dates (EventService). */
public enum EventStatus {
    SCHEDULED,
    CANCELLED,
    FINISHED
}
