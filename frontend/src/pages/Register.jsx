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
  const [passwordErrorMessage, setPasswordErrorMessage] = useState("");
  const [confirmPasswordErrorMessage, setConfirmPasswordErrorMessage] = useState("");

  const handleRegister = async ({ username, email, password, confirmPassword }) => {
    try {
      await register(username, email, password, confirmPassword);
      navigate("/home");
    } catch (err) {
      setEmailErrorMessage(err.email || "");
      setUserErrorMessage(err.username || "");
      setPasswordErrorMessage(err.password || "");
      setConfirmPasswordErrorMessage(err.confirmPassword || "");
    }
  };

  const handleLoginRedirect = async () => {
    navigate("/login");
  };

  return (
    <div className="register-container">
      <h2 className="register-title">Crear Cuenta</h2>
      <p className="register-subtitle">Únete a Asaltamontes Female 💖</p>

      <RegisterForm onSubmit={handleRegister} emailErrorMessage={emailErrorMessage} userErrorMessage={userErrorMessage} passwordErrorMessage={passwordErrorMessage} confirmPasswordErrorMessage={confirmPasswordErrorMessage} />

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
