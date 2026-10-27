package io.github.yagogefaell.asaltamontes.event.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.repository.EventAttendeeRepository;
import io.github.yagogefaell.asaltamontes.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;

/**
 * What the notifications (T18) need to know about an event, so they do not reach into this domain's repositories
 * (D-001). Internal ids stay inside the backend.
 */
@Service
@RequiredArgsConstructor
public class EventNotificationSource {

    /** The bits a notification shows: the title and the start with the event's own zone (D-019). */
    public record EventInfo(UUID publicId, String title, Instant startsAt, String timezone, Long organizerId) {
    }

    private final EventRepository eventRepository;
    private final EventAttendeeRepository attendeeRepository;

    @Transactional(readOnly = true)
    public Optional<EventInfo> find(Long eventId) {
        return eventRepository.findById(eventId)
                .map(e -> new EventInfo(e.getPublicId(), e.getTitle(), e.getStartsAt(), e.getTimezone(), e.getOrganizer().getId()));
    }

    /** Users with the event in one of the given states, in a single query. */
    @Transactional(readOnly = true)
    public List<Long> attendeeUserIds(Long eventId, Collection<AttendeeStatus> statuses) {
        return attendeeRepository.findUserIdsByStatuses(eventId, statuses);
    }
}
