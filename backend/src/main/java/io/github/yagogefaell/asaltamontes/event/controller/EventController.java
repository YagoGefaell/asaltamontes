package io.github.yagogefaell.asaltamontes.event.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.event.dto.CancelEventDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventFilter;
import io.github.yagogefaell.asaltamontes.event.dto.EventAttendeeDto;
import io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventDetailsDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.event.service.UpdateEventReader;
import io.github.yagogefaell.asaltamontes.message.dto.EventChatDto;
import io.github.yagogefaell.asaltamontes.message.service.EventChatService;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UpdateEventReader updateEventReader;
    private final EventChatService eventChatService;

    @PostMapping
    public ResponseEntity<EventDetailsDto> createEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @Valid @RequestBody CreateEventDto request) {
        EventDetailsDto created = eventService.create(request, currentUser);
        return ResponseEntity.created(URI.create("/api/events/" + created.id())).body(created);
    }

    /** Events the user can see, soonest first; by default the upcoming ones. All filters are optional. */
    @GetMapping
    public ResponseEntity<Page<EventSummaryDto>> searchEvents(
            @AuthenticationPrincipal UserAccount currentUser,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) RunningLevel level,
            @RequestParam(required = false) UUID communityId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minDistance,
            @RequestParam(required = false) BigDecimal maxDistance,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng,
            @RequestParam(required = false) String organizer,
            @RequestParam(required = false) String attending,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        EventFilter filter = EventFilter.of(from, to, type, level, communityId, city, minDistance, maxDistance,
                minLat, maxLat, minLng, maxLng, organizer, attending, q);
        return ResponseEntity.ok(eventService.search(currentUser, filter, pageable));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventDetailsDto> getEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(eventService.get(eventId, currentUser));
    }

    /** "Add to calendar" (T21): an iCalendar file the calendar apps open. Same visibility as the event itself. */
    @GetMapping(value = "/{eventId}.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getEventCalendar(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quedada.ics\"")
                .body(eventService.calendar(eventId, currentUser));
    }

    /** The chat of the quedada (T21): only for people who are going; 404 for everybody else. */
    @GetMapping("/{eventId}/chat")
    public ResponseEntity<EventChatDto> getEventChat(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(eventChatService.open(eventId, currentUser));
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<EventDetailsDto> updateEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId,
            @RequestBody JsonNode body) {
        // The raw JSON tells an absent field (no change) from an explicit null (clears it), D-020
        return ResponseEntity.ok(eventService.update(eventId, updateEventReader.read(body), currentUser));
    }

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<EventDetailsDto> cancelEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId,
            @Valid @RequestBody(required = false) CancelEventDto request) {
        return ResponseEntity.ok(eventService.cancel(eventId, request != null ? request.reason() : null, currentUser));
    }

    @PostMapping("/{eventId}/join")
    public ResponseEntity<EventDetailsDto> joinEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(eventService.join(eventId, currentUser));
    }

    @PostMapping("/{eventId}/leave")
    public ResponseEntity<EventDetailsDto> leaveEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(eventService.leave(eventId, currentUser));
    }

    @GetMapping("/{eventId}/attendees")
    public ResponseEntity<Page<EventAttendeeDto>> getAttendees(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable UUID eventId,
            @RequestParam(required = false) AttendeeStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventService.getAttendees(eventId, status, currentUser, pageable));
    }
}
