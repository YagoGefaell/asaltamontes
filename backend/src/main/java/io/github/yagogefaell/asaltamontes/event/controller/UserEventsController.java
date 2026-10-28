package io.github.yagogefaell.asaltamontes.event.controller;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/** Events another user organizes (T31b, D-023). Lives in the event package so user does not depend on event. */
@RestController
@RequestMapping("/api/users/{username}/events")
@RequiredArgsConstructor
public class UserEventsController {

    private final EventService eventService;

    /** Upcoming events she organizes and the caller can see; never her attendance. */
    @GetMapping
    public ResponseEntity<Page<EventSummaryDto>> getUserEvents(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String username,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(eventService.searchByOrganizer(username, currentUser, from, to, pageable));
    }
}
