package io.github.yagogefaell.asaltamontes.auth.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** Email verification settings (asaltamontes.verification.*). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "asaltamontes.verification")
public class VerificationProperties {

    /**
     * Whether unverified accounts are limited (no communities, events, posts or new chats). Off by default while there is
     * no real email: nobody should be blocked without a way to verify (D-025).
     */
    private boolean enforce = false;
    /** How long the link of the verification email works. */
    private Duration tokenTtl = Duration.ofHours(24);
}
