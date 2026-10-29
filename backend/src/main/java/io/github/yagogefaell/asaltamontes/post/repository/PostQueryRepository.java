package io.github.yagogefaell.asaltamontes.post.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Every read of posts goes through here, so the visibility rule lives in one place and is applied in the query
 * (not in memory). A viewer sees a post when it is not deleted, its author has no block with her in either
 * direction (D-022) and either
 * <ul>
 *   <li>it is in a community where she is a member who is not banned (public or private: the text is for members), or</li>
 *   <li>it is on a profile and she is the author or follows her.</li>
 * </ul>
 * Each page is one query for the posts (author, community, like and delete flags included) and one for the photos
 * of those posts, whatever the page size: no N+1.
 *
 * <p>The home feed is "fan-out on read": the same query without a scope. It is bounded by the page size and served
 * by the {@code (community_id|author_id, created_at DESC, public_id DESC)} indexes. Moving to fan-out on write
 * (a {@code feed_items(user_id, post_id)} table filled when posting) is worth it when the feed query visibly slows
 * down (rule of thumb: p95 over ~100 ms on live data, or hundreds of thousands of live posts, or users following
 * thousands of accounts), because then posting costs one row per follower and reading is an index scan on one table.
 */
@Repository
public class PostQueryRepository {

    /** What a page is restricted to besides visibility. */
    public enum Scope { FEED, COMMUNITY, PROFILE }

    /** A post row as the database returns it; the service turns it into a DTO. {@code postId} is internal. */
    public record PostRow(Long postId, UUID publicId, String body, Instant createdAt, long likeCount, long commentCount,
                          String authorUsername, String authorFullName, String authorPicture,
                          String communitySlug, String communityName, boolean likedByMe, boolean canDelete) {
    }

    public record MediaRow(Long postId, UUID mediaId) {
    }

    private static final String SELECT = """
            SELECT new io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository$PostRow(
                p.id, p.publicId, p.body, p.createdAt, p.likeCount, p.commentCount,
                u.username, pr.fullName, pr.profilePictureUrl, c.slug, c.name,
                (CASE WHEN EXISTS (SELECT 1 FROM PostLike l WHERE l.postId = p.id AND l.userId = :viewer)
                      THEN true ELSE false END),
                (CASE WHEN p.authorId = :viewer OR (p.communityId IS NOT NULL AND EXISTS (
                          SELECT 1 FROM CommunityMember sm
                          WHERE sm.community.id = p.communityId AND sm.userAccount.id = :viewer
                            AND sm.role IN (io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.OWNER,
                                            io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.MODERATOR)
                            AND sm.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED))
                      THEN true ELSE false END))
            FROM Post p
            JOIN UserAccount u ON u.id = p.authorId
            LEFT JOIN u.profile pr
            LEFT JOIN Community c ON c.id = p.communityId
            WHERE p.deletedAt IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM UserBlock b
                  WHERE (b.blocker.id = :viewer AND b.blocked.id = p.authorId)
                     OR (b.blocked.id = :viewer AND b.blocker.id = p.authorId))
              AND (
                  (p.communityId IS NOT NULL AND EXISTS (
                      SELECT 1 FROM CommunityMember vm
                      WHERE vm.community.id = p.communityId AND vm.userAccount.id = :viewer
                        AND vm.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED))
                  OR (p.communityId IS NULL AND (p.authorId = :viewer OR EXISTS (
                      SELECT 1 FROM UserFollow f WHERE f.follower.id = :viewer AND f.following.id = p.authorId))))
            """;

    private static final String ORDER = " ORDER BY p.createdAt DESC, p.publicId DESC";

    @PersistenceContext
    private EntityManager entityManager;

    /** Newest first, keyset after {@code cursor}; asks for {@code limit} rows (the service passes size + 1). */
    public List<PostRow> page(Scope scope, Long viewerId, Long scopeId, OpaqueCursor cursor, int limit) {
        StringBuilder jpql = new StringBuilder(SELECT);
        switch (scope) {
            case COMMUNITY -> jpql.append(" AND p.communityId = :scopeId");
            case PROFILE -> jpql.append(" AND p.communityId IS NULL AND p.authorId = :scopeId");
            case FEED -> { }
        }
        if (cursor != null) {
            jpql.append(" AND (p.createdAt < :at OR (p.createdAt = :at AND p.publicId < :pid))");
        }
        jpql.append(ORDER);
        TypedQuery<PostRow> query = entityManager.createQuery(jpql.toString(), PostRow.class)
                .setParameter("viewer", viewerId)
                .setMaxResults(limit);
        if (scope != Scope.FEED) {
            query.setParameter("scopeId", scopeId);
        }
        if (cursor != null) {
            query.setParameter("at", cursor.order().toInstant(ZoneOffset.UTC))
                    .setParameter("pid", cursor.publicId());
        }
        return query.getResultList();
    }

    /** One post if the viewer may see it. */
    public Optional<PostRow> one(Long viewerId, UUID publicId) {
        return entityManager.createQuery(SELECT + " AND p.publicId = :publicId", PostRow.class)
                .setParameter("viewer", viewerId)
                .setParameter("publicId", publicId)
                .getResultStream().findFirst();
    }

    /** Photos of these posts in display order (one query). Rows whose image disappeared are skipped. */
    public List<MediaRow> media(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                SELECT new io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository$MediaRow(pm.postId, pm.mediaId)
                FROM PostMedia pm WHERE pm.postId IN :ids AND pm.mediaId IS NOT NULL
                ORDER BY pm.postId, pm.position
                """, MediaRow.class)
                .setParameter("ids", postIds)
                .getResultList();
    }
}
