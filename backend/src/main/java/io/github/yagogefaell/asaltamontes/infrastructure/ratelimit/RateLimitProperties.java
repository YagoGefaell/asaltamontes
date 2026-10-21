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
}
