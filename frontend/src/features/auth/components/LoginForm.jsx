// src/features/auth/components/LoginForm.jsx
import React, { useState } from "react";
import Button from "../../../shared/components/Button";
import "./LoginForm.css";

function LoginForm({ onSubmit, errorMessage }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();
    if (onSubmit) onSubmit({ email, password });
  };

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="input-group">
        <label>Email</label>
        <input
          type="email"
          placeholder="tuemail@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
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

        {errorMessage && (
          <p className="form-error">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className="error-icon"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              width="16"
              height="16"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4m0 4h.01M12 2a10 10 0 100 20 10 10 0 000-20z"
              />
            </svg>
            {errorMessage}
          </p>
        )}
      </div>

      <Button type="submit" variant="primary">
        Entrar
      </Button>
    </form>
  );
}

export default LoginForm;