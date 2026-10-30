package io.github.yagogefaell.asaltamontes.infrastructure.mail;

/**
 * Outgoing email (T24a). Callers never depend on SMTP: {@link SmtpMailSender} when {@code MAIL_HOST} is set,
 * {@link LoggingMailSender} otherwise. Sending never throws to the caller: a failure is logged and the request goes on.
 */
public interface MailSender {

    void send(OutgoingEmail email);
}
