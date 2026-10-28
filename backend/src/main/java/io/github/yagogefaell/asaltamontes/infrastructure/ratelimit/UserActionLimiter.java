package io.github.yagogefaell.asaltamontes.infrastructure.ratelimit;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException;
import lombok.RequiredArgsConstructor;

/**
 * Per-user limits on actions that can be abused once authenticated (T18): edits of events, groups created, follows, messages in community chats.
 * Per user and not per IP (behind the proxy every request shares one address). The generous limits are properties;
 * going over answers 429 with {@code Retry-After}. Every attempt counts, also the ones that end in an error.
 */
@Component
@RequiredArgsConstructor
public class UserActionLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public void eventEdit(Long userId) {
        check("event-edit", userId, properties.getEventEditsPerHour(), HOUR);
    }

    public void groupCreate(Long userId) {
        check("group-create", userId, properties.getGroupsCreatedPerDay(), DAY);
    }

    public void groupMemberAdd(Long userId) {
        check("group-member-add", userId, properties.getGroupMemberAddsPerHour(), HOUR);
    }

    public void follow(Long userId) {
        check("follow", userId, properties.getFollowsPerHour(), HOUR);
    }

    public void communityMessage(Long userId) {
        check("community-message", userId, properties.getCommunityMessagesPerMinute(), MINUTE);
    }

    public void eventMessage(Long userId) {
        check("event-message", userId, properties.getEventMessagesPerMinute(), MINUTE);
    }

    private void check(String action, Long userId, int limit, Duration period) {
        if (!properties.isEnabled()) {
            return;
        }
        long retryAfter = rateLimiter.tryAcquire(action + ":user:" + userId, limit, period);
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Demasiadas peticiones. Vuelve a probar en " + retryAfter + " segundos", retryAfter);
        }
    }
}
