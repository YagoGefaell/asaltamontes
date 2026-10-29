package io.github.yagogefaell.asaltamontes.post.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.post.domain.PostComment;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /** A live comment of that post. */
    @Query("SELECT c FROM PostComment c WHERE c.publicId = :publicId AND c.postId = :postId AND c.deletedAt IS NULL")
    Optional<PostComment> findLive(@Param("publicId") UUID publicId, @Param("postId") Long postId);

    /** Only the request that flips the row gets 1, so the counter goes down once however many requests race. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PostComment c SET c.deletedAt = :now WHERE c.id = :id AND c.deletedAt IS NULL")
    int markDeleted(@Param("id") Long id, @Param("now") Instant now);
}
