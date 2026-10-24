package io.github.yagogefaell.asaltamontes.media.maintenance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.media.service.MediaCleanupService;
import lombok.extern.slf4j.Slf4j;

/** Daily removal of uploads that were never linked to anything (T17c). */
@Slf4j
@Component
public class MediaOrphanCleanupJob {

    private final MediaCleanupService cleanupService;

    public MediaOrphanCleanupJob(MediaCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${asaltamontes.media.cleanup-cron:0 15 4 * * *}")
    public void run() {
        int deleted = cleanupService.deleteOrphans();
        if (deleted > 0) {
            log.info("Media cleanup: deleted {} orphan images", deleted);
        }
    }
}
