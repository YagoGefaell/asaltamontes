package io.github.yagogefaell.asaltamontes.report.service;

import java.util.UUID;

/** The reported thing once the reporter is known to be able to see it. */
public record ResolvedTarget(Long targetId, UUID targetPublicId, Long reportedUserId, String snapshot) {
}
