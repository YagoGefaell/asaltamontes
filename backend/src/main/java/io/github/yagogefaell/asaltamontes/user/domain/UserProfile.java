package io.github.yagogefaell.asaltamontes.user.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name="user_profiles")
public class UserProfile {

    @Id
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "city")
    private String city;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "running_level")
    private RunningLevel runningLevel;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "followers_count", nullable = false)
    private Long followersCount = 0L;

    @Column(name = "following_count", nullable = false)
    private Long followingCount = 0L;

    @Column(name = "is_public_profile", nullable = false)
    private Boolean isPublicProfile;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Identity by primary key (Hibernate's recommended pattern): no lazy relations in equals/hashCode/toString,
    // stable across persist and valid for Hibernate proxies.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile other)) return false;
        return userId != null && userId.equals(other.getUserId());
    }

    @Override
    public int hashCode() {
        return UserProfile.class.hashCode();
    }

    @Override
    public String toString() {
        return "UserProfile{userId=" + userId + "}";
    }
}
