package io.github.yagogefaell.asaltamontes.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default sender while no SMTP is configured (D-025): the email is written to the log instead of being sent, so the
 * demo works without a mail provider. The log then contains the links with their tokens: it is only meant for a private server.
 */
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(OutgoingEmail email) {
        log.info("MAIL_HOST is not set: email NOT sent. To: {} | Subject: {}\n{}", email.to(), email.subject(), email.textBody());
    }
}
