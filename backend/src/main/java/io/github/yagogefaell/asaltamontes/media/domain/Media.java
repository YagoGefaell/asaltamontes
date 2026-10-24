package io.github.yagogefaell.asaltamontes.media.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Metadata of an uploaded image (T17a). The files themselves live in the MediaStorage. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "media")
public class Media {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status;

    /** Size of the largest variant. */
    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** Sum of the bytes of all the stored variants. */
    @Column(nullable = false)
    private long bytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Storage keys of the three stored variants: {@code <purpose>/<id>/{thumb,medium,large}.jpg}. */
    public List<String> storageKeys() {
        String prefix = purpose.pathSegment() + "/" + id + "/";
        return List.of(prefix + "thumb.jpg", prefix + "medium.jpg", prefix + "large.jpg");
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
