package io.github.yagogefaell.asaltamontes.auth.service;

import io.github.yagogefaell.asaltamontes.infrastructure.mail.OutgoingEmail;

/** Texts of the account emails (Spanish). The link is built by us from configuration and a base64url token: nothing from the user goes in. */
final class AccountEmails {

    private AccountEmails() {
    }

    static OutgoingEmail verifyEmail(String to, String link, long hoursValid) {
        String text = "Hola,\n\n"
                + "Gracias por unirte a Asaltamontes Female. Confirma tu email abriendo este enlace:\n\n"
                + link + "\n\n"
                + "El enlace caduca en " + hoursValid + " horas. Si no has creado una cuenta, ignora este mensaje.\n";
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:0 auto;color:#222\">"
                + "<h2>Confirma tu email</h2>"
                + "<p>Gracias por unirte a Asaltamontes Female. Confirma tu email para activar todas las funciones.</p>"
                + "<p><a href=\"" + link + "\" style=\"display:inline-block;padding:12px 20px;background:#2d6a4f;color:#fff;"
                + "border-radius:8px;text-decoration:none\">Verificar mi email</a></p>"
                + "<p style=\"font-size:13px;color:#666\">El enlace caduca en " + hoursValid
                + " horas. Si no has creado una cuenta, ignora este mensaje.</p></div>";
        return new OutgoingEmail(to, "Confirma tu email en Asaltamontes Female", text, html);
    }
}
