package io.github.yagogefaell.asaltamontes.event.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.event.domain.Event;
import io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto;

public interface EventRepository extends JpaRepository<Event, Long> {

    // Scheduled events from :from, that the user can see: public ones, her own, and those of communities she
    // belongs to (not banned). Soonest first (index events (starts_at, id)). Filters come in T15c.
    @Query(value = """
        SELECT new io.github.yagogefaell.asaltamontes.event.dto.EventSummaryDto(
            e.id, e.title, e.type, e.startsAt, e.timezone, e.meetingPointName, e.city,
            e.distanceKm, e.elevationGainM, e.level, e.capacity, e.attendeeCount,
            c.slug, c.name, o.username, e.imageUrl
        )
        FROM Event e
        JOIN e.organizer o
        LEFT JOIN e.community c
        WHERE e.startsAt >= :from
          AND e.status = io.github.yagogefaell.asaltamontes.event.domain.EventStatus.SCHEDULED
          AND (e.visibility = io.github.yagogefaell.asaltamontes.event.domain.EventVisibility.PUBLIC
               OR o.id = :userId
               OR EXISTS (SELECT 1 FROM CommunityMember m
                          WHERE m.community = c AND m.userAccount.id = :userId
                            AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED))
        ORDER BY e.startsAt ASC, e.id ASC
        """,
        countQuery = """
        SELECT COUNT(e) FROM Event e LEFT JOIN e.community c
        WHERE e.startsAt >= :from
          AND e.status = io.github.yagogefaell.asaltamontes.event.domain.EventStatus.SCHEDULED
          AND (e.visibility = io.github.yagogefaell.asaltamontes.event.domain.EventVisibility.PUBLIC
               OR e.organizer.id = :userId
               OR EXISTS (SELECT 1 FROM CommunityMember m
                          WHERE m.community = c AND m.userAccount.id = :userId
                            AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED))
        """)
    Page<EventSummaryDto> findUpcomingVisibleTo(@Param("userId") Long userId, @Param("from") Instant from, Pageable pageable);
}
