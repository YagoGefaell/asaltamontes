import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth.js";
import { verifyEmailRequest } from "../../features/auth/services/auth.service.js";
import Button from "../../shared/forms/Button";
import FormError from "../../shared/forms/FormError";
import "./VerifyEmail.css";

const INVALID_LINK = "El enlace no es válido o ha caducado. Pide uno nuevo desde el aviso «Verifica tu email».";

/** Destination of the emailed link (T24a2): public, consumes the one-time token by POST. */
export default function VerifyEmail() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const navigate = useNavigate();
  const { isAuthenticated, setCurrentUser } = useAuth();
  const [status, setStatus] = useState(token ? "verifying" : "failed");
  const [error, setError] = useState(token ? "" : INVALID_LINK);
  // The token is single-use: StrictMode's second effect run must not spend it twice
  const started = useRef(false);

  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    verifyEmailRequest(token)
      .then(() => {
        setCurrentUser?.((previous) => (previous ? { ...previous, isVerified: true } : previous));
        setStatus("done");
      })
      .catch((requestError) => {
        setError(requestError.status === 429
          ? "Demasiados intentos. Inténtalo de nuevo en unos minutos."
          : requestError.status === 400
            ? INVALID_LINK
            : "No se ha podido verificar el email. Inténtalo de nuevo.");
        setStatus("failed");
      });
  }, [token, setCurrentUser]);

  const leave = () => navigate(isAuthenticated ? "/home" : "/login", { replace: true });

  return (
    <div className="verify-email">
      <h1 className="verify-email__title">
        {status === "done" ? "Email verificado" : "Verifica tu email"}
      </h1>
      {status === "verifying" && <p className="verify-email__text">Comprobando el enlace...</p>}
      {status === "done" && (
        <>
          <p className="verify-email__text">Ya está todo listo. ¡Bienvenida!</p>
          <Button onClick={leave}>{isAuthenticated ? "Continuar" : "Iniciar sesión"}</Button>
        </>
      )}
      {status === "failed" && (
        <>
          <FormError message={error} />
          <Button variant="secondary" onClick={leave}>{isAuthenticated ? "Ir al inicio" : "Iniciar sesión"}</Button>
        </>
      )}
    </div>
  );
}
