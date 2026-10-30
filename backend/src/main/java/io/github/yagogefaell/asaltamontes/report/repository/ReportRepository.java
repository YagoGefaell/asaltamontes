package io.github.yagogefaell.asaltamontes.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.yagogefaell.asaltamontes.report.domain.Report;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("""
        SELECT COUNT(r) > 0 FROM Report r
        WHERE r.reporterId = :reporterId AND r.targetType = :type AND r.targetId = :targetId
          AND r.status = io.github.yagogefaell.asaltamontes.report.domain.ReportStatus.OPEN
    """)
    boolean existsOpen(@Param("reporterId") Long reporterId, @Param("type") ReportTargetType type, @Param("targetId") Long targetId);
}
