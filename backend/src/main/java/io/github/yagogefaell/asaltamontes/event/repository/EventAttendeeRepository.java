package io.github.yagogefaell.asaltamontes.event.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.event.domain.AttendeeStatus;
import io.github.yagogefaell.asaltamontes.event.domain.EventAttendee;

public interface EventAttendeeRepository extends JpaRepository<EventAttendee, Long> {

    @Query("SELECT a.status FROM EventAttendee a WHERE a.event.id = :eventId AND a.user.id = :userId")
    Optional<AttendeeStatus> findStatus(@Param("eventId") Long eventId, @Param("userId") Long userId);
}
