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

    public void postCreate(Long userId) {
        check("post-create", userId, properties.getPostsPerDay(), DAY);
    }

    public void postLike(Long userId) {
        check("post-like", userId, properties.getPostLikesPerHour(), HOUR);
    }

    public void postComment(Long userId) {
        check("post-comment", userId, properties.getPostCommentsPerHour(), HOUR);
    }

    public void reportCreate(Long userId) {
        check("report-create", userId, properties.getReportsPerDay(), DAY);
    }

    /** "Download my data" (T24d): heavy to build and full of personal data, so a couple per day. */
    public void dataExport(Long userId) {
        check("data-export", userId, properties.getDataExportsPerDay(), DAY);
    }

    /** Verification emails: one per minute (cooldown of the "Resend" button) and a cap per hour. */
    public void verificationEmail(Long userId) {
        check("verification-email-cooldown", userId, 1, MINUTE);
        check("verification-email", userId, properties.getVerificationEmailsPerHour(), HOUR);
    }

    /** "Forgot my password" for an address. Keyed on what was typed, not on whether the account exists, so the answer reveals nothing. */
    public void passwordResetRequest(String email) {
        check("password-reset", email.toLowerCase(java.util.Locale.ROOT), properties.getPasswordResetRequestsPerHour(), HOUR);
    }

    /** Email-change requests of a logged-in user (they check the current password, so they must not allow guessing it). */
    public void emailChangeRequest(Long userId) {
        check("email-change", userId, properties.getEmailChangeRequestsPerHour(), HOUR);
    }

    /**
     * Whether one more account email may go to this address (BUG-084): a cap per hour and per day on the destination,
     * whoever asks for it. False means "do not send"; callers stay silent so nothing is revealed.
     */
    public boolean mayEmail(String address) {
        if (!properties.isEnabled()) {
            return true;
        }
        // "+tag" aliases land in the same mailbox
        String key = "mail-to:" + address.toLowerCase(java.util.Locale.ROOT).replaceFirst("\\+[^@]*@", "@");
        return rateLimiter.tryAcquire(key + ":h", properties.getEmailsPerAddressPerHour(), HOUR) == 0
                && rateLimiter.tryAcquire(key + ":d", properties.getEmailsPerAddressPerDay(), DAY) == 0;
    }

    private void check(String action, String key, int limit, Duration period) {
        if (!properties.isEnabled()) {
            return;
        }
        long retryAfter = rateLimiter.tryAcquire(action + ":key:" + key, limit, period);
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Demasiadas peticiones. Vuelve a probar en " + retryAfter + " segundos", retryAfter);
        }
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
