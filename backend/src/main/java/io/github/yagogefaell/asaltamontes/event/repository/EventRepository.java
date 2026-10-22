package io.github.yagogefaell.asaltamontes.event.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.event.domain.Event;

// The filtered listing (T15c) is in EventSearchRepository
public interface EventRepository extends JpaRepository<Event, Long>, EventSearchRepository {

    // join/leave/promotion of one event run one after the other (T15b): positions and counters stay consistent
    @Query(value = "SELECT id FROM events WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Long> lockById(@Param("id") Long id);

    @Query("SELECT e.attendeeCount FROM Event e WHERE e.id = :id")
    int currentAttendeeCount(@Param("id") Long id);

    // Bulk updates do not bump @Version: joining never makes an organizer's edit fail
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Event e SET e.attendeeCount = e.attendeeCount + :delta WHERE e.id = :id")
    int addToAttendeeCount(@Param("id") Long id, @Param("delta") int delta);
}
