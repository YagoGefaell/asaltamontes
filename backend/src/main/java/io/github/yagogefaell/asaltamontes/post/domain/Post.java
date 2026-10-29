package io.github.yagogefaell.asaltamontes.post.domain;

import java.time.Instant;

import io.github.yagogefaell.asaltamontes.common.identifier.PublicIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A publication (T22): text with optional photos, on the author's profile ({@code communityId == null}) or in a
 * community. Deleting is logical ({@code deletedAt}); the counters are changed with atomic UPDATEs, never from
 * the entity.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "posts")
public class Post extends PublicIdEntity {

    public Post(Long authorId, Long communityId, String body) {
        this.authorId = authorId;
        this.communityId = communityId;
        this.body = body;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "community_id", updatable = false)
    private Long communityId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "like_count", nullable = false, insertable = false, updatable = false)
    private Long likeCount;

    @Column(name = "comment_count", nullable = false, insertable = false, updatable = false)
    private Long commentCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
