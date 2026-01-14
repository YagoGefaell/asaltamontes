// src/features/auth/components/RegisterForm.jsx
import React, { useState } from "react";
import Button from "../../../shared/components/Button";
import "./RegisterForm.css";

export default function RegisterForm({ onSubmit }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();
    if (onSubmit) onSubmit({ name, email, password });
  };

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="input-group">
        <label>Nombre</label>
        <input
          type="text"
          placeholder="Tu nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
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
      </div>

      <Button type="submit" variant="primary">
        Crear Cuenta
      </Button>
    </form>
  );
}
