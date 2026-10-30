package io.github.yagogefaell.asaltamontes.admin.maintenance;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Once a report is resolved, the text of other people it carries (the reporter's details and the copy of the reported
 * content) is only kept for {@code asaltamontes.reports.retention-days} days (default 30): enough to appeal, not forever.
 * The row itself, its reason, status and resolution stay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportRetentionService {

    private final ReportRepository reports;

    @Value("${asaltamontes.reports.retention-days:30}")
    private long retentionDays;

    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void purgeResolvedText() {
        int purged = reports.purgeResolvedBefore(Instant.now().minus(Duration.ofDays(retentionDays)));
        if (purged > 0) {
            log.info("Purged the copied text of {} resolved reports", purged);
        }
    }
}
