package io.github.yagogefaell.asaltamontes.report.dto;

import io.github.yagogefaell.asaltamontes.report.domain.ReportReason;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code target} is the username for USER, the slug for COMMUNITY and the public id (uuid) for the rest. */
public record CreateReportDto(
        @NotNull ReportTargetType targetType,
        @NotBlank @Size(max = 100) String target,
        @NotNull ReportReason reason,
        @Size(max = 1000) String details) {
}
