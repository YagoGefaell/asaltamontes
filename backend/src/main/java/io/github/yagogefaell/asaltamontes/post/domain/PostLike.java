package io.github.yagogefaell.asaltamontes.post.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code userId} likes the post. Only read through queries here; the like/unlike endpoints come with T22a2. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "post_likes")
@IdClass(PostLike.Key.class)
public class PostLike {

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private Long postId;
        private Long userId;
    }

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "user_id")
    private Long userId;
}
