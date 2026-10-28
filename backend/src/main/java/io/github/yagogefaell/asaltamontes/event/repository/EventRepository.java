package io.github.yagogefaell.asaltamontes.event.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.event.domain.Event;

// The filtered listing (T15c) is in EventSearchRepository
public interface EventRepository extends JpaRepository<Event, Long>, EventSearchRepository {

    // The API only knows public ids (D-021); served by the unique index on public_id
    Optional<Event> findByPublicId(UUID publicId);

    // join/leave/promotion of one event run one after the other (T15b): positions and counters stay consistent
    @Query(value = "SELECT id FROM events WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("id") Long id);

    @Query("SELECT e.attendeeCount FROM Event e WHERE e.id = :id")
    int currentAttendeeCount(@Param("id") Long id);

    // Bulk updates do not bump @Version: joining never makes an organizer's edit fail
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Event e SET e.attendeeCount = e.attendeeCount + :delta WHERE e.id = :id")
    int addToAttendeeCount(@Param("id") Long id, @Param("delta") int delta);

    @Query("SELECT e.id FROM Event e WHERE e.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    // What the chat needs to know to decide whether it is still open (T21)
    @Query("SELECT new io.github.yagogefaell.asaltamontes.event.dto.EventTiming(e.status, e.startsAt, e.endsAt) FROM Event e WHERE e.id = :id")
    Optional<io.github.yagogefaell.asaltamontes.event.dto.EventTiming> findTiming(@Param("id") Long id);

    // Same rule as EventService.canView, in one query: public events, her own, or a community she belongs to unbanned
    @Query("""
        SELECT COUNT(e) > 0 FROM Event e
        WHERE e.id = :id AND (e.visibility = io.github.yagogefaell.asaltamontes.event.domain.EventVisibility.PUBLIC
            OR e.organizer.id = :userId
            OR EXISTS (SELECT 1 FROM CommunityMember m WHERE m.community.id = e.community.id AND m.userAccount.id = :userId
                       AND m.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED))
        """)
    boolean canView(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * The next scheduled event of each of these communities, in ONE statement (T31c). A tie on the start time returns
     * several rows for a community; the caller keeps the first. The caller has already checked she is a member, so
     * both visibilities may be shown.
     */
    @Query("""
        SELECT new io.github.yagogefaell.asaltamontes.event.repository.EventRepository$NextEventRow(
            e.community.id, e.publicId, e.title, e.startsAt, e.timezone)
        FROM Event e
        WHERE e.community.id IN :communityIds
          AND e.status = io.github.yagogefaell.asaltamontes.event.domain.EventStatus.SCHEDULED
          AND e.startsAt > :now
          AND e.startsAt = (SELECT MIN(e2.startsAt) FROM Event e2
                            WHERE e2.community.id = e.community.id
                              AND e2.status = io.github.yagogefaell.asaltamontes.event.domain.EventStatus.SCHEDULED
                              AND e2.startsAt > :now)
        ORDER BY e.startsAt, e.id
        """)
    List<NextEventRow> findNextEventsOf(@Param("communityIds") java.util.Collection<Long> communityIds,
                                        @Param("now") java.time.Instant now);

    record NextEventRow(Long communityInternalId, UUID id, String title, java.time.Instant startsAt, String timezone) {
    }

    // The start moved: the reminders of the new date are still to be sent (T21). Native: the columns are not mapped,
    // so saving the entity never touches them and this does not bump @Version.
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE events SET reminder_24h_sent_at = NULL, reminder_2h_sent_at = NULL WHERE id = :id", nativeQuery = true)
    int resetReminders(@Param("id") Long id);
}
