package io.github.yagogefaell.asaltamontes.admin.dto;

import jakarta.validation.constraints.Size;

/** Dismiss or act on a report: an optional note for the record (never shown to the reporter or the reported). */
public record ResolveReportDto(@Size(max = 1000) String resolution) {
}
