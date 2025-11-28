package io.github.yagogefaell.asaltamontes.profiles;

import java.sql.Date;
import java.time.LocalDateTime;

import io.github.yagogefaell.asaltamontes.users.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name="user_profiles")
public class UserProfile {

    @Id
    private Long userId; // mismo que user.id

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "location")
    private String location;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "birthdate")
    private Date birthdate;

    @Column(name = "instagram_profile_url")
    private String instagramProfileUrl;

    @Column(name = "strava_profile_url")
    private String stravaProfileUrl;

    @Column(name = "is_public_profile", nullable = false)
    private boolean isPublicProfile;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
