package io.github.yagogefaell.asaltamontes.auth.service;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.auth.domain.AccountTokenPurpose;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.MultipleFieldConflictException;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.MailProperties;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.MailSender;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;

/**
 * Change of email (T24b, BUG-042): asks for the current password, keeps the new address pending (the token's payload) and
 * only applies it when the link sent to the NEW address is used. The old address is warned twice.
 */
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    private final AccountTokenService tokens;
    private final MailSender mailSender;
    private final MailProperties mailProperties;
    private final VerificationProperties verificationProperties;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserActionLimiter limiter;

    @Transactional
    public void request(UserAccount user, String rawNewEmail, String currentPassword) {
        // Counted before checking the password, so the password cannot be guessed through this endpoint
        limiter.emailChangeRequest(user.getId());

        if (user.getPassword() == null) {
            throw new ConflictException("Tu cuenta entra con Google y no tiene contraseña: no se puede cambiar el email desde aquí");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ForbiddenException("La contraseña no es correcta");
        }
        String newEmail = rawNewEmail.trim();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new MultipleFieldConflictException(Map.of("newEmail", "Ese ya es tu email"));
        }
        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new MultipleFieldConflictException(Map.of("newEmail", "Email ya registrado"));
        }
        if (!limiter.mayEmail(newEmail)) {
            // Cannot be used to flood a third party's inbox (BUG-084)
            throw new ConflictException("Se han enviado ya demasiados correos a ese email. Prueba más tarde");
        }

        String token = tokens.issue(user, AccountTokenPurpose.CHANGE_EMAIL, verificationProperties.getTokenTtl(), newEmail);
        String link = baseUrl() + "/confirm-email-change?token=" + token;
        mailSender.send(AccountEmails.confirmNewEmail(newEmail, link, verificationProperties.getTokenTtl().toHours()));
        mailSender.send(AccountEmails.emailChangeRequested(user.getEmail(), AccountEmails.mask(newEmail)));
    }

    /** Applies the pending change. Every failure is the same 400. The new address counts as verified: the link proves it. */
    @Transactional
    public void confirm(String token) {
        AccountTokenService.Consumed consumed = tokens.consumeWithPayload(token, AccountTokenPurpose.CHANGE_EMAIL)
                .orElseThrow(EmailChangeService::invalidLink);
        String newEmail = consumed.payload();
        UserAccount user = userRepository.findById(consumed.userId()).orElseThrow(EmailChangeService::invalidLink);
        if (newEmail == null || userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw invalidLink();
        }

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.setIsVerified(true);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw invalidLink(); // Someone took the address between the check and now
        }
        // Links sent to the old mailbox must not outlive the address
        tokens.invalidate(user.getId(), AccountTokenPurpose.RESET_PASSWORD);
        tokens.invalidate(user.getId(), AccountTokenPurpose.VERIFY_EMAIL);

        mailSender.send(AccountEmails.emailChanged(oldEmail, AccountEmails.mask(newEmail)));
    }

    private static BadRequestException invalidLink() {
        return new BadRequestException("El enlace no es válido o ha caducado");
    }

    private String baseUrl() {
        String url = mailProperties.getAppBaseUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
