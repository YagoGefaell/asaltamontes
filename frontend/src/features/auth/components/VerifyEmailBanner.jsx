import { useEffect, useState } from "react";
import { useAuth } from "../hooks/useAuth.js";
import { resendVerificationEmailRequest } from "../services/auth.service.js";
import Button from "../../../shared/forms/Button";
import "./VerifyEmailBanner.css";

const COOLDOWN_SECONDS = 60;

function resendError(error) {
  if (error.status === 429) {
    return error.retryAfter > 60
      ? `Has pedido muchos enlaces. Inténtalo de nuevo en ${Math.ceil(error.retryAfter / 60)} min.`
      : "Espera un momento antes de pedir otro enlace.";
  }
  return "No se ha podido enviar el enlace. Inténtalo de nuevo.";
}

/** Reminder shown while the email is unverified (T24a2), with «Reenviar» and a cooldown. Errors stay inline. */
export default function VerifyEmailBanner() {
  const { currentUser, setCurrentUser } = useAuth();
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    if (cooldown <= 0) return undefined;
    const timer = setTimeout(() => setCooldown((seconds) => seconds - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  // Only when the server says so: a missing field (profile still loading) is not "unverified"
  if (!currentUser || currentUser.isVerified !== false) return null;

  const resend = async () => {
    setSending(true);
    setError("");
    try {
      await resendVerificationEmailRequest();
      setSent(true);
      setCooldown(COOLDOWN_SECONDS);
    } catch (requestError) {
      if (requestError.status === 409) {
        setCurrentUser((previous) => (previous ? { ...previous, isVerified: true } : previous));
      } else {
        setError(resendError(requestError));
        if (requestError.status === 429) {
          setCooldown(Math.min(requestError.retryAfter ?? COOLDOWN_SECONDS, COOLDOWN_SECONDS));
        }
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="verify-banner" aria-label="Verifica tu email">
      <div className="verify-banner__text">
        <strong>Verifica tu email</strong>
        <span>
          {sent ? `Te hemos enviado el enlace a ${currentUser.email}.` : "Abre el enlace que te enviamos al registrarte."}
        </span>
        {error && <span className="verify-banner__error" role="alert">{error}</span>}
      </div>
      <Button
        variant="outline"
        size="small"
        className="verify-banner__action"
        onClick={resend}
        loading={sending}
        disabled={cooldown > 0}
      >
        {cooldown > 0 ? `Reenviar (${cooldown} s)` : "Reenviar"}
      </Button>
    </section>
  );
}
