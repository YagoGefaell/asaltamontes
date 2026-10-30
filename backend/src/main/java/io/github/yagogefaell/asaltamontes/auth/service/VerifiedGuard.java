package io.github.yagogefaell.asaltamontes.auth.service;

import org.springframework.stereotype.Component;

import io.github.yagogefaell.asaltamontes.common.exceptions.EmailNotVerifiedException;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/**
 * The single place that decides what an account with an unverified email cannot do (T24a, D-025): creating communities
 * and events, publishing and opening new chats. Services call {@link #require} at the start of those actions.
 * With {@code asaltamontes.verification.enforce=false} (the default) it lets everything through.
 */
@Component
@RequiredArgsConstructor
public class VerifiedGuard {

    private final VerificationProperties properties;

    public void require(UserAccount user) {
        if (properties.isEnforce() && !Boolean.TRUE.equals(user.getIsVerified())) {
            throw new EmailNotVerifiedException();
        }
    }
}
