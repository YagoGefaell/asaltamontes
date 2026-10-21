package io.github.yagogefaell.asaltamontes.auth.maintenance;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.repository.RefreshSessionRepository;

@Service
public class RefreshSessionCleanupService {

    private static final int KEEP_EXPIRED_DAYS = 30;

    private final RefreshSessionRepository refreshSessionRepository;

    public RefreshSessionCleanupService(RefreshSessionRepository refreshSessionRepository) {
        this.refreshSessionRepository = refreshSessionRepository;
    }

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void deleteOldExpiredSessions() {
        refreshSessionRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(KEEP_EXPIRED_DAYS));
    }
}
