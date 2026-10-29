package io.github.yagogefaell.asaltamontes.post.domain;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One photo of a post, in display order. {@code mediaId} goes null if the image row disappears. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "post_media")
@IdClass(PostMedia.Key.class)
public class PostMedia {

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long postId;
        private short position;
    }

    public PostMedia(Long postId, int position, UUID mediaId) {
        this.postId = postId;
        this.position = (short) position;
        this.mediaId = mediaId;
    }

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "position")
    private short position;

    @Column(name = "media_id")
    private UUID mediaId;
}
