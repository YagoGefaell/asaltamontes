package io.github.yagogefaell.asaltamontes.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.domain.AccountTokenPurpose;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.MailProperties;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.MailSender;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.OutgoingEmail;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

/** Verification of the email address (T24a, BUG-025): sends the link and consumes it. */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final AccountTokenService tokens;
    private final MailSender mailSender;
    private final MailProperties mailProperties;
    private final VerificationProperties properties;
    private final UserAccountRepository userRepository;
    private final UserActionLimiter limiter;

    /** Sends the verification link. Never throws: registration must not fail because the email did not go out. */
    public void sendVerificationEmail(UserAccount user) {
        try {
            if (Boolean.TRUE.equals(user.getIsVerified())) {
                return;
            }
            if (!limiter.mayEmail(user.getEmail())) {
                return;
            }
            String token = tokens.issue(user, AccountTokenPurpose.VERIFY_EMAIL, properties.getTokenTtl());
            String link = baseUrl() + "/verify-email?token=" + token;
            mailSender.send(AccountEmails.verifyEmail(user.getEmail(), link, properties.getTokenTtl().toHours()));
        } catch (RuntimeException e) {
            log.warn("Could not prepare the verification email for user {}: {}", user.getId(), e.toString());
        }
    }

    /** "Resend" from the banner: limited per user (cooldown and hourly cap). */
    public void resend(UserAccount user) {
        if (Boolean.TRUE.equals(user.getIsVerified())) {
            throw new ConflictException("Tu email ya está verificado");
        }
        limiter.verificationEmail(user.getId());
        sendVerificationEmail(user);
    }

    /** Uses the token of the link. Unknown, used, replaced or expired tokens all fail the same way. */
    @Transactional
    public void verify(String token) {
        Long userId = tokens.consume(token, AccountTokenPurpose.VERIFY_EMAIL)
                .orElseThrow(() -> new BadRequestException("El enlace no es válido o ha caducado"));
        userRepository.findById(userId).ifPresent(user -> user.setIsVerified(true));
    }

    private String baseUrl() {
        String url = mailProperties.getAppBaseUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
