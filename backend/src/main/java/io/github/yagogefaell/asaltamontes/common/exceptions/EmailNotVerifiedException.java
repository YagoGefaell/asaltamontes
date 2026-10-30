package io.github.yagogefaell.asaltamontes.common.exceptions;

/** The action needs a verified email (403 with {@code code=EMAIL_NOT_VERIFIED}, so the front can show the banner). */
public class EmailNotVerifiedException extends ForbiddenException {
    public EmailNotVerifiedException() {
        super("Verifica tu email para poder hacer esto");
    }
}
