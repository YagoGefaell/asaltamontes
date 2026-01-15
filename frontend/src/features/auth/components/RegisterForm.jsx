// src/features/auth/components/RegisterForm.jsx
import React, { useState } from "react";
import Button from "../../../shared/components/Button";
import FormError from "../../../shared/components/FormError.jsx";
import "./RegisterForm.css";

export default function RegisterForm({ onSubmit, emailErrorMessage, userErrorMessage, passwordErrorMessage }) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();
    if (onSubmit) onSubmit({ username, email, password });
  };

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <div className="input-group">
        <label>Username</label>
        <input
          type="text"
          placeholder="Tu username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />
        <FormError message={userErrorMessage} />
      </div>

      <div className="input-group">
        <label>Email</label>
        <input
          type="email"
          placeholder="tuemail@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <FormError message={emailErrorMessage} />
      </div>

      <div className="input-group">
        <label>Contraseña</label>
        <input
          type="password"
          placeholder="••••••••••"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <FormError message={passwordErrorMessage} />
      </div>

      <Button type="submit" variant="primary">
        Crear Cuenta
      </Button>
    </form>
  );
}
