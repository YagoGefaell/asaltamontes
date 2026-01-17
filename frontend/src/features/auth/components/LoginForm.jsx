import { useState } from "react";
import Button from "../../../shared/components/Button";
import Input from "../../../shared/components/Input.jsx";
import "./LoginForm.css";

function LoginForm({ onSubmit, errorMessage }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = (e) => {
    e.preventDefault();
    if (onSubmit) onSubmit({ username, password });
  };

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <Input
        label="Nombre de Usuario"
        type="text"
        value={username}
        onChange={(e) => setUsername(e.target.value.toLowerCase())}
        placeholder="Usuario"
        required
      />

      <Input
        label="Contraseña"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="••••••••••"
        error={errorMessage}
        required
      />

      <Button type="submit" variant="primary">
        Entrar
      </Button>
    </form>
  );
}

export default LoginForm;