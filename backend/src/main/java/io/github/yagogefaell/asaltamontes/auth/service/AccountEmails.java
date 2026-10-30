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

    private static final String WRAP = "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:0 auto;color:#222\">";

    private static String button(String link, String label) {
        return "<p><a href=\"" + link + "\" style=\"display:inline-block;padding:12px 20px;background:#2d6a4f;color:#fff;"
                + "border-radius:8px;text-decoration:none\">" + label + "</a></p>";
    }

    static OutgoingEmail resetPassword(String to, String link, long minutesValid) {
        String text = "Hola,\n\nHas pedido restablecer tu contraseña de Asaltamontes Female. Elige una nueva abriendo este enlace:\n\n"
                + link + "\n\nEl enlace caduca en " + minutesValid + " minutos y solo sirve una vez. "
                + "Si no lo has pedido tú, ignora este mensaje: tu contraseña no cambia.\n";
        String html = WRAP + "<h2>Restablece tu contraseña</h2><p>Has pedido restablecer tu contraseña de Asaltamontes Female.</p>"
                + button(link, "Elegir una contraseña nueva")
                + "<p style=\"font-size:13px;color:#666\">El enlace caduca en " + minutesValid + " minutos y solo sirve una vez. "
                + "Si no lo has pedido tú, ignora este mensaje: tu contraseña no cambia.</p></div>";
        return new OutgoingEmail(to, "Restablece tu contraseña de Asaltamontes Female", text, html);
    }

    /** For an account that only has Google: there is no password to reset. */
    static OutgoingEmail resetPasswordGoogleOnly(String to) {
        String text = "Hola,\n\nHas pedido restablecer la contraseña, pero tu cuenta de Asaltamontes Female entra con Google y no tiene contraseña. "
                + "Usa el botón «Continuar con Google» para entrar.\nSi no lo has pedido tú, ignora este mensaje.\n";
        String html = WRAP + "<h2>Tu cuenta entra con Google</h2><p>Has pedido restablecer la contraseña, pero tu cuenta no tiene: "
                + "entra con el botón «Continuar con Google».</p><p style=\"font-size:13px;color:#666\">Si no lo has pedido tú, ignora este mensaje.</p></div>";
        return new OutgoingEmail(to, "Tu cuenta de Asaltamontes Female entra con Google", text, html);
    }

    static OutgoingEmail passwordChanged(String to) {
        String text = "Hola,\n\nLa contraseña de tu cuenta de Asaltamontes Female se acaba de cambiar y hemos cerrado tus sesiones abiertas.\n"
                + "Si no has sido tú, restablécela de nuevo desde «¿Olvidaste tu contraseña?» y revisa tu email.\n";
        String html = WRAP + "<h2>Tu contraseña ha cambiado</h2><p>Hemos cerrado tus sesiones abiertas.</p>"
                + "<p style=\"font-size:13px;color:#666\">Si no has sido tú, restablécela de nuevo desde «¿Olvidaste tu contraseña?» y revisa tu email.</p></div>";
        return new OutgoingEmail(to, "Tu contraseña de Asaltamontes Female ha cambiado", text, html);
    }

    /** To the NEW address: the link that proves it is hers. */
    static OutgoingEmail confirmNewEmail(String to, String link, long hoursValid) {
        String text = "Hola,\n\nHas pedido usar este email en tu cuenta de Asaltamontes Female. Confírmalo abriendo este enlace:\n\n"
                + link + "\n\nEl enlace caduca en " + hoursValid + " horas. Si no lo has pedido tú, ignora este mensaje.\n";
        String html = WRAP + "<h2>Confirma tu nuevo email</h2><p>Has pedido usar este email en tu cuenta de Asaltamontes Female.</p>"
                + button(link, "Confirmar el cambio")
                + "<p style=\"font-size:13px;color:#666\">El enlace caduca en " + hoursValid + " horas. Si no lo has pedido tú, ignora este mensaje.</p></div>";
        return new OutgoingEmail(to, "Confirma tu nuevo email en Asaltamontes Female", text, html);
    }

    /** To the OLD address when a change is requested, so the owner can react. */
    static OutgoingEmail emailChangeRequested(String to, String maskedNewAddress) {
        String text = "Hola,\n\nAlguien con la contraseña de tu cuenta de Asaltamontes Female ha pedido cambiar el email a " + maskedNewAddress + ". "
                + "El cambio no se aplica hasta que se confirme desde ese buzón.\nSi no has sido tú, cambia tu contraseña cuanto antes.\n";
        String html = WRAP + "<h2>Cambio de email solicitado</h2><p>Se ha pedido cambiar el email de tu cuenta a <b>" + maskedNewAddress
                + "</b>. No se aplica hasta que se confirme desde ese buzón.</p>"
                + "<p style=\"font-size:13px;color:#666\">Si no has sido tú, cambia tu contraseña cuanto antes.</p></div>";
        return new OutgoingEmail(to, "Se ha pedido cambiar el email de tu cuenta", text, html);
    }

    /** To the OLD address once the change is done. */
    static OutgoingEmail emailChanged(String to, String maskedNewAddress) {
        String text = "Hola,\n\nEl email de tu cuenta de Asaltamontes Female ha cambiado a " + maskedNewAddress + ". "
                + "Ya no recibirás aquí los avisos de la cuenta.\nSi no has sido tú, contacta con nosotras cuanto antes.\n";
        String html = WRAP + "<h2>Tu email ha cambiado</h2><p>El email de tu cuenta es ahora <b>" + maskedNewAddress + "</b>.</p>"
                + "<p style=\"font-size:13px;color:#666\">Si no has sido tú, contacta con nosotras cuanto antes.</p></div>";
        return new OutgoingEmail(to, "El email de tu cuenta ha cambiado", text, html);
    }

    /** "ana@example.com" -> "a**@example.com": enough to recognise it, not enough to reveal it. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        // The text goes into an HTML email and the address is whatever the requester typed: only harmless characters survive
        return (email.charAt(0) + "***" + email.substring(at)).replaceAll("[^A-Za-z0-9._@*+-]", "?");
    }
}
