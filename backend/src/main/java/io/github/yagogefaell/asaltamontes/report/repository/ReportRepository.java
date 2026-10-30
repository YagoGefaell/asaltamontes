package io.github.yagogefaell.asaltamontes.report.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.report.domain.Report;
import io.github.yagogefaell.asaltamontes.report.domain.ReportStatus;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("""
        SELECT COUNT(r) > 0 FROM Report r
        WHERE r.reporterId = :reporterId AND r.targetType = :type AND r.targetId = :targetId
          AND r.status = io.github.yagogefaell.asaltamontes.report.domain.ReportStatus.OPEN
    """)
    boolean existsOpen(@Param("reporterId") Long reporterId, @Param("type") ReportTargetType type, @Param("targetId") Long targetId);

    /** A report of hers on this target that was dismissed after {@code since}: the cooldown against insisting. */
    @Query("""
        SELECT COUNT(r) > 0 FROM Report r
        WHERE r.reporterId = :reporterId AND r.targetType = :type AND r.targetId = :targetId
          AND r.status = io.github.yagogefaell.asaltamontes.report.domain.ReportStatus.DISMISSED AND r.handledAt > :since
    """)
    boolean existsDismissedSince(@Param("reporterId") Long reporterId, @Param("type") ReportTargetType type,
                                 @Param("targetId") Long targetId, @Param("since") java.time.Instant since);

    /** Closes one report if it is still open (a second moderator or a double click gets 0). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Report r SET r.status = :status, r.handledBy = :by, r.handledAt = :at, r.resolution = :resolution
        WHERE r.id = :id AND r.status = io.github.yagogefaell.asaltamontes.report.domain.ReportStatus.OPEN
    """)
    int resolve(@Param("id") Long id, @Param("status") ReportStatus status, @Param("by") Long by,
                @Param("at") Instant at, @Param("resolution") String resolution);

    /** Closes every other open report about the same target (the content is gone: they are all answered). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Report r SET r.status = :status, r.handledBy = :by, r.handledAt = :at, r.resolution = :resolution
        WHERE r.targetType = :type AND r.targetId = :targetId
          AND r.status = io.github.yagogefaell.asaltamontes.report.domain.ReportStatus.OPEN
    """)
    int resolveOpenOnTarget(@Param("type") ReportTargetType type, @Param("targetId") Long targetId, @Param("status") ReportStatus status,
                            @Param("by") Long by, @Param("at") Instant at, @Param("resolution") String resolution);

    /** Drops the copied text of the reports resolved before {@code cutoff}: users' words are not kept longer than needed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Report r SET r.details = NULL, r.contentSnapshot = NULL
        WHERE r.status <> io.github.yagogefaell.asaltamontes.report.domain.ReportStatus.OPEN AND r.handledAt < :cutoff
          AND (r.details IS NOT NULL OR r.contentSnapshot IS NOT NULL)
    """)
    int purgeResolvedBefore(@Param("cutoff") Instant cutoff);
}
