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

    /**
     * Likes the post, if it is alive and, when it is in a community, she is still a member who is not banned (decided in
     * the statement, so a ban committed a moment ago cannot be overtaken). Returns 1 only when the like is new.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO post_likes (post_id, user_id, created_at)
        SELECT p.id, :userId, now() FROM posts p
        WHERE p.id = :postId AND p.deleted_at IS NULL
          AND (p.community_id IS NULL OR EXISTS (
              SELECT 1 FROM community_members m
              WHERE m.community_id = p.community_id AND m.user_account_id = :userId AND m.status <> 'BANNED'))
        ON CONFLICT (post_id, user_id) DO NOTHING
        """, nativeQuery = true)
    int insertLike(@Param("postId") Long postId, @Param("userId") Long userId);

    /** 1 only when she had liked it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId", nativeQuery = true)
    int deleteLike(@Param("postId") Long postId, @Param("userId") Long userId);

    /** Atomic counter changes (never read-modify-write); callers only use them when the like/comment row really changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :id AND p.likeCount + :delta >= 0")
    int addToLikeCount(@Param("id") Long id, @Param("delta") long delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :id AND p.commentCount + :delta >= 0")
    int addToCommentCount(@Param("id") Long id, @Param("delta") long delta);

    @Query("SELECT COUNT(l) > 0 FROM PostLike l WHERE l.postId = :postId AND l.userId = :userId")
    boolean isLikedBy(@Param("postId") Long postId, @Param("userId") Long userId);

    @Query("SELECT p.likeCount FROM Post p WHERE p.id = :id")
    long likeCountOf(@Param("id") Long id);
}
