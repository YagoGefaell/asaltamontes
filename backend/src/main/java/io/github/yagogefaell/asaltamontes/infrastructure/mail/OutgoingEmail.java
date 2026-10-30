package io.github.yagogefaell.asaltamontes.infrastructure.mail;

/** An email ready to be sent: a plain-text body and an HTML one with the same content. */
public record OutgoingEmail(String to, String subject, String textBody, String htmlBody) {
}
