import { useEffect, useState } from "react";
import { useAuth } from "../hooks/useAuth.js";
import { resendVerificationEmailRequest } from "../services/auth.service.js";
import Button from "../../../shared/forms/Button";
import FormError from "../../../shared/forms/FormError";
import "./VerifyEmailPanel.css";

const COOLDOWN_SECONDS = 60;

function resendError(error) {
  if (error.status === 429) {
    return error.retryAfter > 60
      ? `Has pedido muchos enlaces. Inténtalo de nuevo en ${Math.ceil(error.retryAfter / 60)} min.`
      : "Espera un momento antes de pedir otro enlace.";
  }
  return "No se ha podido enviar el enlace. Inténtalo de nuevo.";
}

/**
 * Email verification status and «Reenviar» (T24a2), shared by the «Verifica tu email» screen and Ajustes.
 * Cooldown and errors stay inline.
 */
export default function VerifyEmailPanel() {
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

  if (!currentUser) return null;

  if (currentUser.isVerified !== false) {
    return (
      <p className="verify-panel__verified">Tu email {currentUser.email} está verificado.</p>
    );
  }

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
    <div className="verify-panel">
      <p className="verify-panel__text">
        {sent
          ? `Te hemos enviado el enlace a ${currentUser.email}. Ábrelo para verificarlo.`
          : `Tu email ${currentUser.email} aún no está verificado. Abre el enlace que te enviamos al registrarte.`}
      </p>
      <FormError message={error} />
      <Button onClick={resend} loading={sending} disabled={cooldown > 0}>
        {cooldown > 0 ? `Reenviar (${cooldown} s)` : "Reenviar enlace"}
      </Button>
    </div>
  );
}
