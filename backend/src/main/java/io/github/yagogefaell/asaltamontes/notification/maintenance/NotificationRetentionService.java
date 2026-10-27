package io.github.yagogefaell.asaltamontes.notification.maintenance;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.yagogefaell.asaltamontes.notification.repository.NotificationRepository;

/**
 * Retention of notifications (T18): read ones live 90 days, unread ones 180 (confirmed by Yago). Deleted in batches,
 * each in its own short transaction, so a large backlog never holds a long lock.
 */
@Service
public class NotificationRetentionService {

    static final Duration READ_RETENTION = Duration.ofDays(90);
    static final Duration UNREAD_RETENTION = Duration.ofDays(180);
    static final int BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionService.class);

    private final NotificationRepository notificationRepository;
    private final TransactionTemplate transactionTemplate;

    public NotificationRetentionService(NotificationRepository notificationRepository, TransactionTemplate transactionTemplate) {
        this.notificationRepository = notificationRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        purge(Instant.now());
    }

    /** Returns how many rows were deleted. */
    public int purge(Instant now) {
        int read = deleteInBatches(now.minus(READ_RETENTION), true);
        int unread = deleteInBatches(now.minus(UNREAD_RETENTION), false);
        if (read + unread > 0) {
            log.info("Notification retention: deleted {} read and {} unread", read, unread);
        }
        return read + unread;
    }

    private int deleteInBatches(Instant cutoff, boolean read) {
        int total = 0;
        int deleted;
        do {
            Integer count = transactionTemplate.execute(status -> notificationRepository.deleteOlderThan(cutoff, read, BATCH_SIZE));
            deleted = count == null ? 0 : count;
            total += deleted;
        } while (deleted == BATCH_SIZE);
        return total;
    }
}
