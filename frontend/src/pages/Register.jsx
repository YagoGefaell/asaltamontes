import { useNavigate } from "react-router-dom";
import RegisterForm from "../features/auth/components/RegisterForm.jsx";
import { useAuth } from "../features/auth/hooks/useAuth.js";
import { useState } from "react";
import "./Register.css";

const Register = () => {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [errorMessage, setErrorMessage] = useState("");

  const handleRegister = async ({ name, email, password }) => {
    try {
      await register(name, email, password);
      navigate("/home");
    } catch (err) {
      setErrorMessage(err.message);
    }
  };

  const handleLoginRedirect = async () => {
    navigate("/login");
  };

  return (
    <div className="register-container">
      <h2 className="register-title">Crear Cuenta</h2>
      <p className="register-subtitle">Únete a Asaltamontes Female 💖</p>

      <RegisterForm onSubmit={handleRegister} errorMessage={errorMessage} />

      <p className="register-login">
        ¿Ya tienes cuenta?{" "}
        <span
          style={{ cursor: "pointer", color: "var(--primary)" }}
          onClick={handleLoginRedirect}
        >
          Iniciar sesión
        </span>
      </p>
    </div>
  );
};

export default Register;
