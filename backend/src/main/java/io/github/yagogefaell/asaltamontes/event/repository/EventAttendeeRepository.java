package io.github.yagogefaell.asaltamontes.event.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.domain.EventAttendee;
import io.github.yagogefaell.asaltamontes.event.dto.EventAttendeeDto;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, Long> {

    @Query("SELECT a.status FROM EventAttendee a WHERE a.event.id = :eventId AND a.user.id = :userId")
    Optional<AttendeeStatus> findStatus(@Param("eventId") Long eventId, @Param("userId") Long userId);

    @Query("SELECT a.user.id FROM EventAttendee a WHERE a.event.id = :eventId AND a.status IN :statuses")
    List<Long> findUserIdsByStatuses(@Param("eventId") Long eventId, @Param("statuses") java.util.Collection<AttendeeStatus> statuses);

    Optional<EventAttendee> findByEventIdAndUserId(Long eventId, Long userId);

    @Query("SELECT COALESCE(MAX(a.waitlistPosition), 0) FROM EventAttendee a WHERE a.event.id = :eventId AND a.status = :status")
    int maxWaitlistPosition(@Param("eventId") Long eventId, @Param("status") AttendeeStatus status);

    @Query("""
        SELECT a FROM EventAttendee a
        WHERE a.event.id = :eventId AND a.status = io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus.WAITLIST
        ORDER BY a.waitlistPosition ASC
        """)
    List<EventAttendee> findWaitlist(@Param("eventId") Long eventId, Limit limit);

    // Close the gap left by someone who left the waitlist or was promoted
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE EventAttendee a SET a.waitlistPosition = a.waitlistPosition - 1
        WHERE a.event.id = :eventId
          AND a.status = io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus.WAITLIST
          AND a.waitlistPosition > :position
        """)
    int shiftWaitlistAfter(@Param("eventId") Long eventId, @Param("position") int position);

    @Query(value = """
        SELECT new io.github.yagogefaell.asaltamontes.event.dto.EventAttendeeDto(
            u.username, p.fullName, p.profilePictureUrl, a.status, a.waitlistPosition, a.joinedAt
        )
        FROM EventAttendee a JOIN a.user u LEFT JOIN u.profile p
        WHERE a.event.id = :eventId AND a.status = :status
          AND (u.id = :currentUserId OR NOT EXISTS (
              SELECT 1 FROM UserBlock bl
              WHERE (bl.blocker.id = :currentUserId AND bl.blocked.id = u.id)
                 OR (bl.blocked.id = :currentUserId AND bl.blocker.id = u.id)
          ))
        ORDER BY a.waitlistPosition ASC NULLS FIRST, a.joinedAt ASC, a.id ASC
        """,
        countQuery = """
        SELECT COUNT(a) FROM EventAttendee a
        WHERE a.event.id = :eventId AND a.status = :status
          AND (a.user.id = :currentUserId OR NOT EXISTS (
              SELECT 1 FROM UserBlock bl
              WHERE (bl.blocker.id = :currentUserId AND bl.blocked.id = a.user.id)
                 OR (bl.blocked.id = :currentUserId AND bl.blocker.id = a.user.id)
          ))
        """)
    Page<EventAttendeeDto> findAttendees(@Param("eventId") Long eventId, @Param("status") AttendeeStatus status,
                                         @Param("currentUserId") Long currentUserId, Pageable pageable);
}
