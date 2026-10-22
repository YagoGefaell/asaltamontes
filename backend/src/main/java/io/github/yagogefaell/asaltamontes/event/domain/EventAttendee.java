package io.github.yagogefaell.asaltamontes.event.domain;

import java.time.Instant;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One person in an event: going, on the waitlist or cancelled (T15). Same shape as CommunityMember. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "event_attendees",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_attendees_event_user", columnNames = {"event_id", "user_id"})
)
public class EventAttendee {

    public EventAttendee(Event event, UserAccount user, AttendeeStatus status) {
        this.event = event;
        this.user = user;
        this.status = status;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendeeStatus status;

    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
