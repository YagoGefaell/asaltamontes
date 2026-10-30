package io.github.yagogefaell.asaltamontes.auth.maintenance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.github.yagogefaell.asaltamontes.auth.service.AccountTokenService;
import lombok.RequiredArgsConstructor;

/** Deletes the account tokens that expired without being used. */
@Service
@RequiredArgsConstructor
public class AccountTokenCleanupService {

    private final AccountTokenService tokens;

    @Scheduled(cron = "0 45 4 * * *")
    public void deleteExpiredTokens() {
        tokens.deleteExpired();
    }
}
