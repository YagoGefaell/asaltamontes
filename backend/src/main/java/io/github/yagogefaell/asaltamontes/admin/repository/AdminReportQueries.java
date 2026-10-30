package io.github.yagogefaell.asaltamontes.admin.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import io.github.yagogefaell.asaltamontes.report.domain.Report;
import io.github.yagogefaell.asaltamontes.report.domain.ReportStatus;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/** Reading side of the moderation queue: reports with the usernames around them, keyset paginated. */
@Repository
public class AdminReportQueries {

    /** A report with the usernames of the people involved and how many open reports its target has. */
    public record Row(Report report, String reporterUsername, String reportedUsername, String handledByUsername, long openOnTarget) {
    }

    private static final String SELECT = """
        SELECT new io.github.yagogefaell.asaltamontes.admin.repository.AdminReportQueries$Row(
            r, rep.username, tu.username, hb.username,
            (SELECT COUNT(o) FROM Report o WHERE o.status = io.github.yagogefaell.asaltamontes.report.domain.ReportStatus.OPEN
                AND o.targetType = r.targetType AND o.targetId = r.targetId))
        FROM Report r
        JOIN UserAccount rep ON rep.id = r.reporterId
        LEFT JOIN UserAccount tu ON tu.id = r.reportedUserId
        LEFT JOIN UserAccount hb ON hb.id = r.handledBy
        """;

    @PersistenceContext
    private EntityManager em;

    /**
     * One page: the open queue is read oldest first (what waited longest goes first), the history newest first.
     * {@code afterCreatedAt}/{@code afterId} are the keys of the last row of the previous page (both null for the first).
     */
    public List<Row> page(ReportStatus status, ReportTargetType type, boolean oldestFirst, Instant afterCreatedAt, UUID afterId, int limit) {
        StringBuilder jpql = new StringBuilder(SELECT).append("WHERE 1 = 1 ");
        if (status != null) {
            jpql.append("AND r.status = :status ");
        }
        if (type != null) {
            jpql.append("AND r.targetType = :type ");
        }
        String cmp = oldestFirst ? ">" : "<";
        if (afterCreatedAt != null) {
            jpql.append("AND (r.createdAt ").append(cmp).append(" :at OR (r.createdAt = :at AND r.publicId ").append(cmp).append(" :after)) ");
        }
        String dir = oldestFirst ? "ASC" : "DESC";
        jpql.append("ORDER BY r.createdAt ").append(dir).append(", r.publicId ").append(dir);

        TypedQuery<Row> query = em.createQuery(jpql.toString(), Row.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (type != null) {
            query.setParameter("type", type);
        }
        if (afterCreatedAt != null) {
            query.setParameter("at", afterCreatedAt);
            query.setParameter("after", afterId);
        }
        return query.setMaxResults(limit).getResultList();
    }

    public Optional<Row> byPublicId(UUID publicId) {
        return em.createQuery(SELECT + "WHERE r.publicId = :id", Row.class).setParameter("id", publicId)
                .getResultStream().findFirst();
    }
}
