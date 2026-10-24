package io.github.yagogefaell.asaltamontes.community.domain;

import java.time.LocalDateTime;

import io.github.yagogefaell.asaltamontes.common.identifier.PublicIdEntity;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "communities")
public class Community extends PublicIdEntity {

    public Community(String name, String slug, String description, String imageUrl, UserAccount createdBy) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.imageUrl = imageUrl;
        this.createdBy = createdBy;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique regardless of case (index on lower(name), V4)
    @Column(nullable = false, length = 100)
    private String name;

    // Identifier in URLs (T14)
    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** URL of the "medium" variant of {@link #coverMediaId}, or a legacy external URL when that is null. */
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "cover_media_id")
    private java.util.UUID coverMediaId;

    @Column(name = "member_count", nullable = false)
    private Long memberCount = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private UserAccount createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
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