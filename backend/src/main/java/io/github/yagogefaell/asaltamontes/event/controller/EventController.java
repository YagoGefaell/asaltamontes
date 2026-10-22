package io.github.yagogefaell.asaltamontes.event.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import io.github.yagogefaell.asaltamontes.event.dto.CancelEventDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventAttendeeDto;
import io.github.yagogefaell.asaltamontes.event.dto.CreateEventDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventDetailsDto;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import io.github.yagogefaell.asaltamontes.event.dto.UpdateEventDto;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventDetailsDto> createEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @Valid @RequestBody CreateEventDto request) {
        EventDetailsDto created = eventService.create(request, currentUser);
        return ResponseEntity.created(URI.create("/api/events/" + created.id())).body(created);
    }

    /** Upcoming events the user can see, soonest first. Filters: T15c. */
    @GetMapping
    public ResponseEntity<Page<EventSummaryDto>> getUpcomingEvents(
            @AuthenticationPrincipal UserAccount currentUser,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventService.listUpcoming(currentUser, pageable));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventDetailsDto> getEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.get(eventId, currentUser));
    }

    @PatchMapping("/{eventId}")
    public ResponseEntity<EventDetailsDto> updateEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventDto changes) {
        return ResponseEntity.ok(eventService.update(eventId, changes, currentUser));
    }

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<EventDetailsDto> cancelEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long eventId,
            @Valid @RequestBody(required = false) CancelEventDto request) {
        return ResponseEntity.ok(eventService.cancel(eventId, request != null ? request.reason() : null, currentUser));
    }

    @PostMapping("/{eventId}/join")
    public ResponseEntity<EventDetailsDto> joinEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.join(eventId, currentUser));
    }

    @PostMapping("/{eventId}/leave")
    public ResponseEntity<EventDetailsDto> leaveEvent(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long eventId) {
        return ResponseEntity.ok(eventService.leave(eventId, currentUser));
    }

    @GetMapping("/{eventId}/attendees")
    public ResponseEntity<Page<EventAttendeeDto>> getAttendees(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable Long eventId,
            @RequestParam(required = false) AttendeeStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventService.getAttendees(eventId, status, currentUser, pageable));
    }
}
