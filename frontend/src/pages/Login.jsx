import { useNavigate } from "react-router-dom";
import { useAuth } from "../features/auth/hooks/useAuth.js";
import LoginForm from "../features/auth/components/LoginForm.jsx"; // Importa tu formulario real
import "./Login.css";
import { useState } from "react";

function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [errorMessage, setErrorMessage] = useState("");

  const handleLogin = async ({ username, password }) => {
    try {
      await login(username, password);
      setErrorMessage(""); // limpiar error si todo va bien
      navigate("/home");
    } catch (err) {
      setErrorMessage(err.message);
    }
  };

  const handleRegisterRedirect = () => {
    navigate("/register");
  };

  return (
    <div className="login-container">
      <h2 className="login-title">Iniciar Sesión</h2>
      <p className="login-subtitle">Bienvenida de nuevo 💖</p>

      <LoginForm onSubmit={handleLogin} errorMessage={errorMessage} />

      <p className="login-register">
        ¿No tienes cuenta?{" "}
        <span
          style={{ cursor: "pointer", color: "var(--primary)" }}
          onClick={handleRegisterRedirect}
        >
          Crear cuenta
        </span>
      </p>
    </div>
  );
};

export default Login;
