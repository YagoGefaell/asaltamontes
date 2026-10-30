package io.github.yagogefaell.asaltamontes.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import io.github.yagogefaell.asaltamontes.infrastructure.mail.MailSender;
import io.github.yagogefaell.asaltamontes.infrastructure.mail.OutgoingEmail;

/** Fake {@link MailSender} for integration tests: keeps what would have been sent. */
@TestComponent
@Primary
public class RecordingMailSender implements MailSender {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<OutgoingEmail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(OutgoingEmail email) {
        sent.add(email);
    }

    public List<OutgoingEmail> sentTo(String address) {
        return sent.stream().filter(email -> email.to().equalsIgnoreCase(address)).toList();
    }

    /** The token of the link in the last email sent to the address. */
    public String lastTokenSentTo(String address) {
        List<OutgoingEmail> emails = sentTo(address);
        if (emails.isEmpty()) {
            throw new AssertionError("No email was sent to " + address);
        }
        Matcher matcher = TOKEN.matcher(emails.get(emails.size() - 1).textBody());
        if (!matcher.find()) {
            throw new AssertionError("The email has no link with a token");
        }
        return matcher.group(1);
    }
}
