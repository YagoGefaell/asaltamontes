// src/features/auth/components/RegisterForm.jsx
import { useState } from "react";
import Button from "../../../shared/components/Button";
import Input from "../../../shared/components/Input.jsx";
import "./RegisterForm.css";

export default function RegisterForm({
  onSubmit,
  emailErrorMessage,
  userErrorMessage,
  passwordErrorMessage,
  confirmPasswordErrorMessage,
}) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();
    if (onSubmit) onSubmit({ username, email, password, confirmPassword });
  };

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <Input
        label="Nombre de Usuario"
        type="text"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        placeholder="tuusuario"
        error={userErrorMessage}
        required
      />

      <Input
        label="Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="tuemail@example.com"
        error={emailErrorMessage}
        required
      />

      <Input
        label="Contraseña"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="••••••••••"
        error={passwordErrorMessage}
        required
      />

      <Input
        label="Confirmar Contraseña"
        type="password"
        value={confirmPassword}
        onChange={(e) => setConfirmPassword(e.target.value)}
        placeholder={password ? "" : "••••••••••"}
        error={confirmPasswordErrorMessage}
        required
      />

      <Button type="submit" variant="primary">
        Crear Cuenta
      </Button>
    </form>
  );
}
