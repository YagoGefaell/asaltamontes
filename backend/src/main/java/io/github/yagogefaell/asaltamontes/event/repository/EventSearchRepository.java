package io.github.yagogefaell.asaltamontes.event.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import io.github.yagogefaell.asaltamontes.event.dto.EventFilter;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;

/** Event listing with optional filters (T15c). Implemented by {@link EventSearchRepositoryImpl}. */
public interface EventSearchRepository {

    /**
     * Scheduled events starting at or after {@code from} that the user can see, soonest first. The COMMUNITY
     * visibility rule is always part of the query, whatever the filters are.
     */
    Page<EventSummaryDto> search(Long userId, Instant from, EventFilter filter, Pageable pageable);
}
