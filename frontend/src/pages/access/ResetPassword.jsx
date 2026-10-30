import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { resetPasswordRequest } from "../../features/auth/services/auth.service.js";
import Button from "../../shared/forms/Button";
import FormError from "../../shared/forms/FormError";
import Input from "../../shared/forms/Input";
import { validators } from "../../utils/validation.js";
import "./VerifyEmail.css";

const INVALID_LINK = "El enlace no es válido o ha caducado. Pide uno nuevo.";
const MISMATCH = "Las contraseñas no coinciden";

/** Destination of the emailed reset link (T24b2): public, the token is the proof. */
export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState("");
  const [linkFailed, setLinkFailed] = useState(!token);
  const [sending, setSending] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    const errors = {
      password: validators.password(password) || "",
      confirmPassword: password !== confirmPassword ? MISMATCH : "",
    };
    setFieldErrors(errors);
    if (errors.password || errors.confirmPassword) return;
    setSending(true);
    try {
      await resetPasswordRequest({ token, password, confirmPassword });
      setDone(true);
    } catch (requestError) {
      if (requestError.status === 400) {
        setLinkFailed(true);
      } else if (requestError.status === 409) {
        setFieldErrors({ confirmPassword: MISMATCH });
      } else if (requestError.status === 429) {
        setError("Demasiados intentos. Inténtalo de nuevo en unos minutos.");
      } else {
        setError("No se ha podido cambiar la contraseña. Inténtalo de nuevo.");
      }
    } finally {
      setSending(false);
    }
  };

  if (done) {
    return (
      <div className="verify-email">
        <h1 className="verify-email__title">Contraseña cambiada</h1>
        <p className="verify-email__text">Ya puedes iniciar sesión con tu contraseña nueva.</p>
        <Button onClick={() => navigate("/login", { replace: true })}>Iniciar sesión</Button>
      </div>
    );
  }

  if (linkFailed) {
    return (
      <div className="verify-email">
        <h1 className="verify-email__title">Enlace no válido</h1>
        <FormError message={INVALID_LINK} />
        <Button onClick={() => navigate("/forgot-password", { replace: true })}>Pedir un enlace nuevo</Button>
      </div>
    );
  }

  return (
    <div className="verify-email">
      <h1 className="verify-email__title">Elige una contraseña nueva</h1>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <Input
          label="Contraseña nueva"
          id="reset-password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder="••••••••••"
          error={fieldErrors.password}
          required
        />
        <Input
          label="Repite la contraseña"
          id="reset-confirm-password"
          type="password"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          placeholder="••••••••••"
          error={fieldErrors.confirmPassword}
          required
        />
        <FormError message={error} />
        <Button type="submit" loading={sending}>Cambiar contraseña</Button>
      </form>
      <Link to="/login" className="verify-email__link">Volver a iniciar sesión</Link>
    </div>
  );
}
