package io.github.yagogefaell.asaltamontes.post.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.post.domain.Post;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * Logical delete. Only the request that flips the row gets 1, so a double click (or two moderators) releases the
     * photos once and the second one is a 404.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.deletedAt = :now WHERE p.id = :id AND p.deletedAt IS NULL")
    int markDeleted(@Param("id") Long id, @Param("now") Instant now);

    /** Media of the post, to release them when it is deleted. */
    @Query("SELECT pm.mediaId FROM PostMedia pm WHERE pm.postId = :postId AND pm.mediaId IS NOT NULL")
    List<UUID> findMediaIds(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PostMedia pm WHERE pm.postId = :postId")
    int deleteMedia(@Param("postId") Long postId);
}
