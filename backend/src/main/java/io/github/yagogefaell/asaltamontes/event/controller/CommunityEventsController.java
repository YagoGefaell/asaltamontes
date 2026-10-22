package io.github.yagogefaell.asaltamontes.event.controller;

import java.math.BigDecimal;
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

import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.event.dto.EventFilter;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/** Events of one community (T15c). Lives in the event package so community does not depend on event. */
@RestController
@RequestMapping("/api/communities/{slug}/events")
@RequiredArgsConstructor
public class CommunityEventsController {

    private final EventService eventService;

    /** Upcoming events of the community that the user can see; same filters as GET /api/events (except communityId). */
    @GetMapping
    public ResponseEntity<Page<EventSummaryDto>> getCommunityEvents(
            @AuthenticationPrincipal UserAccount currentUser,
            @PathVariable String slug,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) EventType type,
            @RequestParam(required = false) RunningLevel level,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal minDistance,
            @RequestParam(required = false) BigDecimal maxDistance,
            @RequestParam(required = false) String attending,
            @PageableDefault(size = 20) Pageable pageable) {
        EventFilter filter = EventFilter.of(from, to, type, level, null, city, minDistance, maxDistance,
                null, null, null, null, null, attending);
        return ResponseEntity.ok(eventService.searchByCommunity(slug, currentUser, filter, pageable));
    }
}
