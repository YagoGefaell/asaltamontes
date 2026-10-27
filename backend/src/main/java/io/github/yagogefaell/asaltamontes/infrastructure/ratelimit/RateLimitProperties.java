package io.github.yagogefaell.asaltamontes.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** Requests per minute allowed on the authentication endpoints (asaltamontes.rate-limit.*). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "asaltamontes.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    /** Per IP and, separately, per username. */
    private int loginPerMinute = 5;
    /** Per IP. */
    private int registerPerMinute = 3;
    /** Per IP. */
    private int refreshPerMinute = 30;
    /** Per user (not per IP: behind the proxy every IP looks the same), for POST /api/notifications/read. */
    private int notificationsReadPerMinute = 60;
    /** Per user: PATCH of events (T18). */
    private int eventEditsPerHour = 30;
    /** Per user: groups created (T18). */
    private int groupsCreatedPerDay = 10;
    /** Per user: follows (T18). */
    private int followsPerHour = 100;
    /** Per user: messages in community chats, where each one reaches every member (T20). */
    private int communityMessagesPerMinute = 20;
}
