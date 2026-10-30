import { useState } from "react";
import { Link } from "react-router-dom";
import { forgotPasswordRequest } from "../../features/auth/services/auth.service.js";
import Button from "../../shared/forms/Button";
import FormError from "../../shared/forms/FormError";
import Input from "../../shared/forms/Input";
import { validators } from "../../utils/validation.js";
import "./VerifyEmail.css";

function requestError(error) {
  if (error.status === 429) {
    return error.retryAfter > 60
      ? `Has pedido muchos enlaces. Inténtalo de nuevo en ${Math.ceil(error.retryAfter / 60)} min.`
      : "Espera un momento antes de pedir otro enlace.";
  }
  return "No se ha podido enviar el enlace. Inténtalo de nuevo.";
}

/** «¿Olvidaste tu contraseña?» (T24b2). The answer is the same whether or not the address has an account. */
export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [fieldError, setFieldError] = useState("");
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    const address = email.trim().toLowerCase();
    const invalid = validators.email(address);
    setFieldError(invalid || "");
    setError("");
    if (invalid) return;
    setSending(true);
    try {
      await forgotPasswordRequest(address);
      setSent(true);
    } catch (requestFailure) {
      setError(requestError(requestFailure));
    } finally {
      setSending(false);
    }
  };

  if (sent) {
    return (
      <div className="verify-email">
        <h1 className="verify-email__title">Revisa tu correo</h1>
        <p className="verify-email__text">
          Si hay una cuenta con ese email, te hemos enviado un enlace para elegir una contraseña nueva.
          Es válido durante 30 minutos.
        </p>
        <Link to="/login" className="verify-email__link">Volver a iniciar sesión</Link>
      </div>
    );
  }

  return (
    <div className="verify-email">
      <h1 className="verify-email__title">Recupera tu contraseña</h1>
      <p className="verify-email__text">Escribe tu email y te enviaremos un enlace para elegir una nueva.</p>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <Input
          label="Email"
          id="forgot-email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="email@example.com"
          error={fieldError}
          required
        />
        <FormError message={error} />
        <Button type="submit" loading={sending}>Enviar enlace</Button>
      </form>
      <Link to="/login" className="verify-email__link">Volver a iniciar sesión</Link>
    </div>
  );
}
