package io.github.yagogefaell.asaltamontes.infrastructure.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/** SMTP settings (asaltamontes.mail.*, from MAIL_*). With an empty host nothing is sent: see {@link LoggingMailSender}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "asaltamontes.mail")
public class MailProperties {

    private String host = "";
    private int port = 587;
    private String username = "";
    private String password = "";
    /** Sender address, e.g. "Asaltamontes Female <hola@tudominio.com>". */
    private String from = "Asaltamontes Female <no-reply@localhost>";
    /** STARTTLS (port 587). For implicit TLS (port 465) set it to false and ssl to true. */
    private boolean starttls = true;
    private boolean ssl = false;
    /** Public URL of the web app; the links in the emails are built from it. */
    private String appBaseUrl = "http://localhost";

    public boolean isConfigured() {
        return host != null && !host.isBlank();
    }
}
