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
 *   <li>it is in a community she is not banned from and that is public, or private and she is a member (D-025), or</li>
 *   <li>it is on a profile she may see ({@code PROFILE_VISIBLE}: a public profile, herself or an accepted follower).</li>
 * </ul>
 * Each page is one query for the posts (author, community, like and delete flags included) and one for the photos
 * of those posts, whatever the page size: no N+1.
 *
 * <p>The home feed adds {@code IN_HOME_FEED} (her communities and the people she follows) and is "fan-out on read": the same query without a scope. It is bounded by the page size and served
 * by the {@code (community_id|author_id, created_at DESC, public_id DESC)} indexes. Moving to fan-out on write
 * (a {@code feed_items(user_id, post_id)} table filled when posting) is worth it when the feed query visibly slows
 * down (rule of thumb: p95 over ~100 ms on live data, or hundreds of thousands of live posts, or users following
 * thousands of accounts), because then posting costs one row per follower and reading is an index scan on one table.
 */
@Repository
public class PostQueryRepository {

    /** What a page is restricted to besides visibility. */
    public enum Scope { FEED, COMMUNITY, PROFILE, EXPLORE }

    /** A post row as the database returns it; the service turns it into a DTO. {@code postId} is internal. */
    public record PostRow(Long postId, UUID publicId, String body, Instant createdAt, long likeCount, long commentCount,
                          String authorUsername, String authorFullName, String authorPicture,
                          String communitySlug, String communityName, boolean likedByMe, boolean canDelete,
                          boolean staff, Long authorId, Long communityId) {
    }

    /** A comment as the database returns it. */
    public record CommentRow(UUID publicId, String body, Instant createdAt, String authorUsername, String authorFullName,
                             String authorPicture, boolean canDelete) {
    }

    public record MediaRow(Long postId, UUID mediaId) {
    }

    /**
     * Who may see the posts on somebody's own profile (D-025, T23a): a public profile, herself, or an accepted
     * follower of a private one (blocks are excluded separately). {@code p} is the post, {@code pr} the profile of
     * its author and {@code :viewer} the viewer; the single place that knows.
     */
    private static final String PROFILE_VISIBLE = """
            pr.isPublicProfile = true OR p.authorId = :viewer
                      OR EXISTS (SELECT 1 FROM UserFollow pf WHERE pf.follower.id = :viewer AND pf.following.id = p.authorId)""";

    /** The viewer is the owner or a moderator (not banned) of the community of the post {@code p}. */
    private static final String STAFF = """
            EXISTS (SELECT 1 FROM CommunityMember sm
                    WHERE sm.community.id = p.communityId AND sm.userAccount.id = :viewer
                      AND sm.role IN (io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.OWNER,
                                      io.github.yagogefaell.asaltamontes.community.domain.CommunityRole.MODERATOR)
                      AND sm.status <> io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED)""";

    /**
     * D-026: moderating inside her own community the owner and the moderators see everything, also what a block
     * (in either direction) would hide. Only used where the viewer is moderating (the wall of the community and one
     * post to read or delete); the feed, profiles, explore and interacting keep the block.
     */
    private static final String MODERATION_BYPASS = " OR (p.communityId IS NOT NULL AND " + STAFF + ")";

    private static final String SELECT = """
            SELECT new io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository$PostRow(
                p.id, p.publicId, p.body, p.createdAt, p.likeCount, p.commentCount,
                u.username, pr.fullName, pr.profilePictureUrl, c.slug, c.name,
                (CASE WHEN EXISTS (SELECT 1 FROM PostLike l WHERE l.postId = p.id AND l.userId = :viewer)
                      THEN true ELSE false END),
                (CASE WHEN p.authorId = :viewer OR @STAFF@ THEN true ELSE false END),
                (CASE WHEN @STAFF@ THEN true ELSE false END),
                p.authorId, p.communityId)
            FROM Post p
            JOIN UserAccount u ON u.id = p.authorId
            LEFT JOIN u.profile pr
            LEFT JOIN Community c ON c.id = p.communityId
            WHERE p.deletedAt IS NULL
              AND (NOT EXISTS (
                  SELECT 1 FROM UserBlock b
                  WHERE (b.blocker.id = :viewer AND b.blocked.id = p.authorId)
                     OR (b.blocked.id = :viewer AND b.blocker.id = p.authorId))@BYPASS@)
              AND (
                  (p.communityId IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM CommunityMember bm
                          WHERE bm.community.id = p.communityId AND bm.userAccount.id = :viewer
                            AND bm.status = io.github.yagogefaell.asaltamontes.community.domain.MemberStatus.BANNED)
                      AND (c.visibility = io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility.PUBLIC
                           OR EXISTS (
                               SELECT 1 FROM CommunityMember vm
                               WHERE vm.community.id = p.communityId AND vm.userAccount.id = :viewer)))
                  OR (p.communityId IS NULL AND (@PROFILE@
                  )))
            """;

    private static String select(boolean moderating) {
        return SELECT.replace("@STAFF@", STAFF).replace("@PROFILE@", PROFILE_VISIBLE)
                .replace("@BYPASS@", moderating ? MODERATION_BYPASS : "");
    }

    /** Her communities, and herself and the people she follows: what her home feed is made of. */
    private static final String IN_HOME_FEED = """
             (
                  (p.communityId IS NOT NULL AND EXISTS (
                      SELECT 1 FROM CommunityMember fm
                      WHERE fm.community.id = p.communityId AND fm.userAccount.id = :viewer))
                  OR (p.communityId IS NULL AND (p.authorId = :viewer OR EXISTS (
                      SELECT 1 FROM UserFollow f WHERE f.follower.id = :viewer AND f.following.id = p.authorId))))
            """;

    /** Explore (T22a2): posts with a photo from public communities and profiles, minus what is already in her home feed. */
    private static final String EXPLORE_ONLY = """
             AND EXISTS (SELECT 1 FROM PostMedia xm WHERE xm.postId = p.id AND xm.mediaId IS NOT NULL)
             AND (p.communityId IS NULL
                  OR c.visibility = io.github.yagogefaell.asaltamontes.community.domain.CommunityVisibility.PUBLIC)
             AND NOT""" + IN_HOME_FEED;

    private static final String ORDER = " ORDER BY p.createdAt DESC, p.publicId DESC";

    @PersistenceContext
    private EntityManager entityManager;

    /** Newest first, keyset after {@code cursor}; asks for {@code limit} rows (the service passes size + 1). */
    public List<PostRow> page(Scope scope, Long viewerId, Long scopeId, OpaqueCursor cursor, int limit) {
        // Only the wall of a community is a moderation view (D-026)
        StringBuilder jpql = new StringBuilder(select(scope == Scope.COMMUNITY));
        switch (scope) {
            case COMMUNITY -> jpql.append(" AND p.communityId = :scopeId");
            case PROFILE -> jpql.append(" AND p.communityId IS NULL AND p.authorId = :scopeId");
            case FEED -> jpql.append(" AND").append(IN_HOME_FEED);
            case EXPLORE -> jpql.append(EXPLORE_ONLY);
        }
        if (cursor != null) {
            jpql.append(" AND (p.createdAt < :at OR (p.createdAt = :at AND p.publicId < :pid))");
        }
        jpql.append(ORDER);
        TypedQuery<PostRow> query = entityManager.createQuery(jpql.toString(), PostRow.class)
                .setParameter("viewer", viewerId)
                .setMaxResults(limit);
        if (scope == Scope.COMMUNITY || scope == Scope.PROFILE) {
            query.setParameter("scopeId", scopeId);
        }
        if (cursor != null) {
            query.setParameter("at", cursor.order().toInstant(ZoneOffset.UTC))
                    .setParameter("pid", cursor.publicId());
        }
        return query.getResultList();
    }

    /**
     * One post if the viewer may see it. {@code moderating}: she is reading or deleting, so the owner and the
     * moderators of its community also get what a block hides (D-026); to interact it is {@code false}.
     */
    public Optional<PostRow> one(Long viewerId, UUID publicId, boolean moderating) {
        return entityManager.createQuery(select(moderating) + " AND p.publicId = :publicId", PostRow.class)
                .setParameter("viewer", viewerId)
                .setParameter("publicId", publicId)
                .getResultStream().findFirst();
    }

    /**
     * Comments of a post, oldest first, keyset after {@code cursor}. A comment of somebody blocked in either
     * direction is left out, unless the viewer moderates the community of the post ({@code staff}, D-026): then
     * she sees and may delete them all. {@code postOwner}: she wrote the profile post, so she may delete any
     * comment on it (D-027), but a block still hides it. One query whatever the page size.
     */
    public List<CommentRow> comments(Long postId, Long viewerId, boolean staff, boolean postOwner, OpaqueCursor cursor, int limit) {
        StringBuilder jpql = new StringBuilder("""
                SELECT new io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository$CommentRow(
                    cm.publicId, cm.body, cm.createdAt, u.username, pr.fullName, pr.profilePictureUrl,
                    (CASE WHEN cm.authorId = :viewer OR :staff = true OR :postOwner = true THEN true ELSE false END))
                FROM PostComment cm
                JOIN UserAccount u ON u.id = cm.authorId
                LEFT JOIN u.profile pr
                WHERE cm.postId = :postId AND cm.deletedAt IS NULL
                  AND (:staff = true OR NOT EXISTS (
                      SELECT 1 FROM UserBlock b
                      WHERE (b.blocker.id = :viewer AND b.blocked.id = cm.authorId)
                         OR (b.blocked.id = :viewer AND b.blocker.id = cm.authorId)))
                """);
        if (cursor != null) {
            jpql.append(" AND (cm.createdAt > :at OR (cm.createdAt = :at AND cm.publicId > :pid))");
        }
        jpql.append(" ORDER BY cm.createdAt, cm.publicId");
        TypedQuery<CommentRow> query = entityManager.createQuery(jpql.toString(), CommentRow.class)
                .setParameter("viewer", viewerId)
                .setParameter("staff", staff)
                .setParameter("postOwner", postOwner)
                .setParameter("postId", postId)
                .setMaxResults(limit);
        if (cursor != null) {
            query.setParameter("at", cursor.order().toInstant(ZoneOffset.UTC))
                    .setParameter("pid", cursor.publicId());
        }
        return query.getResultList();
    }

    /** One comment (for the answer to the one who just wrote it). */
    public CommentRow comment(UUID publicId, Long viewerId, boolean staff, boolean postOwner) {
        return entityManager.createQuery("""
                SELECT new io.github.yagogefaell.asaltamontes.post.repository.PostQueryRepository$CommentRow(
                    cm.publicId, cm.body, cm.createdAt, u.username, pr.fullName, pr.profilePictureUrl,
                    (CASE WHEN cm.authorId = :viewer OR :staff = true OR :postOwner = true THEN true ELSE false END))
                FROM PostComment cm JOIN UserAccount u ON u.id = cm.authorId LEFT JOIN u.profile pr
                WHERE cm.publicId = :publicId
                """, CommentRow.class)
                .setParameter("viewer", viewerId)
                .setParameter("staff", staff)
                .setParameter("postOwner", postOwner)
                .setParameter("publicId", publicId)
                .getSingleResult();
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
