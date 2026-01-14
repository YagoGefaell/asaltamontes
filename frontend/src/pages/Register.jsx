import React from "react";
import { useNavigate } from "react-router-dom";
import RegisterForm from "../features/auth/components/RegisterForm.jsx";
import "./Register.css";

const Register = () => {
  const navigate = useNavigate();

  const handleLoginRedirect = () => {
    navigate("/login"); // Redirige a la página de login
  };

  return (
    <div className="register-container">
      <h2 className="register-title">Crear Cuenta</h2>
      <p className="register-subtitle">Únete a Asaltamontes Female 💖</p>

      {/* Aquí insertamos el RegisterForm */}
      <RegisterForm />

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
