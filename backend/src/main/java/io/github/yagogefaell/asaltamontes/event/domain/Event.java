package io.github.yagogefaell.asaltamontes.event.domain;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.yagogefaell.asaltamontes.community.domain.Community;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A meetup, training, race or trip (T15). Times are instants; {@code timezone} is the one shown to users. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.SCHEDULED;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(nullable = false, length = 40)
    private String timezone;

    @Column(name = "meeting_point_name", length = 200)
    private String meetingPointName;

    private Double latitude;

    private Double longitude;

    @Column(length = 80)
    private String city;

    @Column(name = "distance_km", precision = 5, scale = 1)
    private BigDecimal distanceKm;

    @Column(name = "elevation_gain_m")
    private Integer elevationGainM;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RunningLevel level;

    // null = no limit
    private Integer capacity;

    // GOING attendees. Only the bulk UPDATEs of EventRepository change it (T15b): saving the entity never writes
    // it, so an edit loaded before someone joined cannot put the old number back
    @Column(name = "attendee_count", nullable = false, updatable = false)
    private int attendeeCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventVisibility visibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id")
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private UserAccount organizer;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // Two people editing at once: the second save fails (409) instead of overwriting the first
    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
