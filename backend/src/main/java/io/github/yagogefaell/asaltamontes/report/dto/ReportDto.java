package io.github.yagogefaell.asaltamontes.report.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.yagogefaell.asaltamontes.report.domain.ReportReason;
import io.github.yagogefaell.asaltamontes.report.domain.ReportStatus;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;

/** What the reporter gets back. Nothing about the reported account. */
public record ReportDto(UUID id, ReportTargetType targetType, ReportReason reason, ReportStatus status, Instant createdAt) {
}
