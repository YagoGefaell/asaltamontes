package io.github.yagogefaell.asaltamontes.notification.controller;

import java.time.Duration;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.RateLimitProperties;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.RateLimiter;
import io.github.yagogefaell.asaltamontes.notification.dto.MarkReadRequest;
import io.github.yagogefaell.asaltamontes.notification.dto.NotificationPageDto;
import io.github.yagogefaell.asaltamontes.notification.dto.NotificationPreferencesDto;
import io.github.yagogefaell.asaltamontes.notification.dto.UnreadCountDto;
import io.github.yagogefaell.asaltamontes.notification.dto.UpdatePreferencesRequest;
import io.github.yagogefaell.asaltamontes.notification.service.NotificationService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/** The caller's own notifications (T18): nobody can read or touch anyone else's. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Duration READ_PERIOD = Duration.ofMinutes(1);

    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @GetMapping
    public NotificationPageDto list(@AuthenticationPrincipal UserAccount currentUser,
                                    @RequestParam(required = false) String before,
                                    @RequestParam(defaultValue = "" + NotificationService.DEFAULT_PAGE) int limit) {
        return notificationService.list(currentUser.getId(), before, limit);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unreadCount(@AuthenticationPrincipal UserAccount currentUser) {
        return new UnreadCountDto(notificationService.unreadCount(currentUser.getId()));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserAccount currentUser,
                                         @RequestBody MarkReadRequest request) {
        // Per user, not per IP: behind the proxy every request shares one address
        if (rateLimitProperties.isEnabled()) {
            long retryAfter = rateLimiter.tryAcquire("notifications-read:user:" + currentUser.getId(),
                    rateLimitProperties.getNotificationsReadPerMinute(), READ_PERIOD);
            if (retryAfter > 0) {
                throw new TooManyRequestsException("Demasiadas peticiones. Vuelve a probar en " + retryAfter + " segundos", retryAfter);
            }
        }
        notificationService.markRead(currentUser.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public NotificationPreferencesDto preferences(@AuthenticationPrincipal UserAccount currentUser) {
        return notificationService.preferences(currentUser.getId());
    }

    @PatchMapping("/preferences")
    public NotificationPreferencesDto updatePreferences(@AuthenticationPrincipal UserAccount currentUser,
                                                        @RequestBody UpdatePreferencesRequest request) {
        return notificationService.updatePreferences(currentUser.getId(), request);
    }
}
