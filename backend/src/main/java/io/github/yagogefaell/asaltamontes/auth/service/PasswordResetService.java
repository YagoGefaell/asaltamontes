package io.github.yagogefaell.asaltamontes.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.domain.AccountTokenPurpose;
import io.github.yagogefaell.asaltamontes.auth.repository.RefreshSessionRepository;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.MultipleFieldConflictException;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.MailProperties;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.MailSender;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

/**
 * "I forgot my password" (T24b). The request answers the same whether or not the address belongs to an account (no
 * enumeration); using the link sets the new password, revokes every session and warns by email. Extends the auth
 * of {@link AuthService} (D-012), does not replace it.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final AccountTokenService tokens;
    private final MailSender mailSender;
    private final MailProperties mailProperties;
    private final UserAccountRepository userRepository;
    private final RefreshSessionRepository refreshSessions;
    private final PasswordEncoder passwordEncoder;
    private final UserActionLimiter limiter;

    /** Never reveals whether the address exists. Never logs the address or the token. */
    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail.trim();
        limiter.passwordResetRequest(email);

        UserAccount user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        // Own cap (3/hour per typed address); not the shared per-address one, or verification emails could block a legitimate reset
        if (user == null) {
            return;
        }
        try {
            if (user.getPassword() == null) {
                mailSender.send(AccountEmails.resetPasswordGoogleOnly(user.getEmail()));
                return;
            }
            String token = tokens.issue(user, AccountTokenPurpose.RESET_PASSWORD, TOKEN_TTL);
            String link = baseUrl() + "/reset-password?token=" + token;
            mailSender.send(AccountEmails.resetPassword(user.getEmail(), link, TOKEN_TTL.toMinutes()));
        } catch (RuntimeException e) {
            log.warn("Could not prepare the password reset email for user {}: {}", user.getId(), e.getClass().getSimpleName());
        }
    }

    /** Unknown, used, replaced or expired tokens all fail the same way (400). The password is validated before the token is spent. */
    @Transactional
    public void reset(String token, String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new MultipleFieldConflictException(Map.of("confirmPassword", "Las contraseñas no coinciden"));
        }
        Long userId = tokens.consume(token, AccountTokenPurpose.RESET_PASSWORD)
                .orElseThrow(() -> new BadRequestException("El enlace no es válido o ha caducado"));
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("El enlace no es válido o ha caducado"));

        user.setPasswordHash(passwordEncoder.encode(password));
        // She has just proven she controls the mailbox (also closes BUG-083 for whoever pre-registered her address)
        user.setIsVerified(true);
        refreshSessions.revokeAllByUserId(userId, LocalDateTime.now());
        // A change of email still pending was requested with the old password: it no longer stands
        tokens.invalidate(userId, AccountTokenPurpose.CHANGE_EMAIL);

        mailSender.send(AccountEmails.passwordChanged(user.getEmail()));
    }

    private String baseUrl() {
        String url = mailProperties.getAppBaseUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
