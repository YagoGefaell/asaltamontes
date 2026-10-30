package io.github.yagogefaell.asaltamontes.admin.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.report.domain.ReportReason;
import io.github.yagogefaell.asaltamontes.report.domain.ReportStatus;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;

/**
 * A report in the queue or in its detail. {@code details} and {@code contentSnapshot} are free text written by users:
 * clients must show them as plain text, never as HTML. Both become {@code null} once the retention period of a
 * resolved report has passed. {@code target} is the username (USER), slug (COMMUNITY) or public id (the rest).
 * {@code contentAvailable} says whether the reported thing still exists (null when it cannot be told).
 */
public record AdminReportDto(
        UUID id,
        ReportTargetType targetType,
        String target,
        ReportReason reason,
        String details,
        String contentSnapshot,
        ReportStatus status,
        Instant createdAt,
        String reporterUsername,
        String reportedUsername,
        long openReportsOnTarget,
        Boolean contentAvailable,
        String handledByUsername,
        Instant handledAt,
        String resolution) {
}
