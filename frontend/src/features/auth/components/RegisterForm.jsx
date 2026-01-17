// src/features/auth/components/RegisterForm.jsx
import { useState } from "react";
import Button from "../../../shared/components/Button";
import Input from "../../../shared/components/Input.jsx";
import "./RegisterForm.css";

export default function RegisterForm({
  onSubmit,
  emailErrorMessage,
  usernameErrorMessage,
  fullNameErrorMessage,
  passwordErrorMessage,
  confirmPasswordErrorMessage,
}) {
  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const capitalizeWords = (text = "") =>
  text
    .toLowerCase()
    .trim()
    .split(" ")
    .filter(Boolean)
    .map(
      word => word.charAt(0).toUpperCase() + word.slice(1)
    )
    .join(" ");


  const handleSubmit = (e) => {
    e.preventDefault();
    if (onSubmit) onSubmit({ fullName: capitalizeWords(fullName), username: username.toLowerCase(), email: email.toLowerCase(), password, confirmPassword });
  };

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <Input
        label="Nombre Completo"
        type="text"
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        placeholder="Nombre Completo"
        error={fullNameErrorMessage}
        required
      />

      <Input
        label="Nombre de Usuario"
        type="text"
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        placeholder="Usuario"
        error={usernameErrorMessage}
        required
      />

      <Input
        label="Email"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="email@example.com"
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
