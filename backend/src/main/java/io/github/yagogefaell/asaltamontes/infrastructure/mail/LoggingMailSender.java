package io.github.yagogefaell.asaltamontes.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default sender while no SMTP is configured (D-025): the email is not sent. With {@code MAIL_LOG_BODY=true} its body is
 * written to the log instead, so the demo works without a provider; that body holds the links with their tokens, so it is
 * only meant for a private server. Without it only the subject is logged, never the recipient or any token (BUG-084).
 */
public class LoggingMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    private final boolean logBody;

    public LoggingMailSender(boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public void send(OutgoingEmail email) {
        if (logBody) {
            log.info("MAIL_HOST is not set: email NOT sent. To: {} | Subject: {}\n{}", email.to(), email.subject(), email.textBody());
        } else {
            log.info("MAIL_HOST is not set: email NOT sent. Subject: {} (set MAIL_LOG_BODY=true to see its body in the log)", email.subject());
        }
    }
}
