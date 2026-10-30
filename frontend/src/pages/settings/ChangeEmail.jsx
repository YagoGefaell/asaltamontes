import "./SettingsMenu.css";
import { useState } from "react";
import { useAuth } from "../../features/auth/hooks/useAuth.js";
import { requestEmailChangeRequest } from "../../features/auth/services/auth.service.js";
import Button from "../../shared/forms/Button";
import FormError from "../../shared/forms/FormError";
import Input from "../../shared/forms/Input";
import { validators } from "../../utils/validation.js";

function requestError(error) {
  switch (error.status) {
    case 403:
      return { field: "currentPassword", message: "La contraseña no es correcta" };
    case 409:
      return { form: "No se puede usar ese email. Prueba con otro." };
    case 429:
      return {
        form: error.retryAfter > 60
          ? `Has hecho muchos intentos. Inténtalo de nuevo en ${Math.ceil(error.retryAfter / 60)} min.`
          : "Espera un momento antes de volver a intentarlo.",
      };
    case 400:
      return { field: "newEmail", message: "Formato de email inválido" };
    default:
      return { form: "No se ha podido enviar el enlace. Inténtalo de nuevo." };
  }
}

/** Ajustes → Seguridad → Cambiar email (T24b2). Nothing changes until she opens the link sent to the new address. */
export default function ChangeEmail() {
  const { currentUser } = useAuth();
  const [newEmail, setNewEmail] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState("");
  const [sending, setSending] = useState(false);
  const [sentTo, setSentTo] = useState("");

  const handleSubmit = async (event) => {
    event.preventDefault();
    const address = newEmail.trim().toLowerCase();
    setError("");
    const errors = {
      newEmail: validators.email(address) || "",
      currentPassword: currentPassword ? "" : "Escribe tu contraseña actual",
    };
    setFieldErrors(errors);
    if (errors.newEmail || errors.currentPassword) return;
    setSending(true);
    try {
      await requestEmailChangeRequest({ newEmail: address, currentPassword });
      setSentTo(address);
      setCurrentPassword("");
    } catch (requestFailure) {
      const { field, message, form } = requestError(requestFailure);
      setFieldErrors(field ? { [field]: message } : {});
      setError(form ?? "");
    } finally {
      setSending(false);
    }
  };

  if (sentTo) {
    return (
      <div className="settings-menu">
        <p>
          Te hemos enviado un enlace a {sentTo}. Ábrelo para confirmar el cambio; hasta entonces
          seguirás usando {currentUser?.email ?? "tu email actual"}. El enlace es válido durante 24 horas.
        </p>
      </div>
    );
  }

  return (
    <div className="settings-menu">
      <form className="form" onSubmit={handleSubmit} noValidate>
        {currentUser?.email && <p>Tu email actual es {currentUser.email}.</p>}
        <Input
          label="Email nuevo"
          id="new-email"
          type="email"
          autoComplete="email"
          value={newEmail}
          onChange={(event) => setNewEmail(event.target.value)}
          placeholder="email@example.com"
          error={fieldErrors.newEmail}
          required
        />
        <Input
          label="Contraseña actual"
          id="current-password"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
          placeholder="••••••••••"
          error={fieldErrors.currentPassword}
          required
        />
        <FormError message={error} />
        <Button type="submit" loading={sending}>Enviar enlace</Button>
      </form>
    </div>
  );
}
