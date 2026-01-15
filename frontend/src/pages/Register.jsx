import { useNavigate } from "react-router-dom";
import RegisterForm from "../features/auth/components/RegisterForm.jsx";
import { useAuth } from "../features/auth/hooks/useAuth.js";
import { useState } from "react";
import "./Register.css";

const Register = () => {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [emailErrorMessage, setEmailErrorMessage] = useState("");
  const [userErrorMessage, setUserErrorMessage] = useState("");

  const handleRegister = async ({ name, email, password }) => {
    try {
      await register(name, email, password);
      navigate("/home");
    } catch (err) {
      setEmailErrorMessage(err.email || "");
      setUserErrorMessage(err.username || "");
    }
  };

  const handleLoginRedirect = async () => {
    navigate("/login");
  };

  return (
    <div className="register-container">
      <h2 className="register-title">Crear Cuenta</h2>
      <p className="register-subtitle">Únete a Asaltamontes Female 💖</p>

      <RegisterForm onSubmit={handleRegister} emailErrorMessage={emailErrorMessage} userErrorMessage={userErrorMessage} />

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
