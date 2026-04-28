import { useNavigate } from "react-router-dom";
import RegisterForm from "../../features/auth/components/RegisterForm.jsx";
import { useAuth } from "../../features/auth/hooks/useAuth.js";
import { GoogleLoginButton } from '../../shared/components/GoogleLoginButton.jsx';
import { useState } from "react";
import "./Register.css";

const Register = () => {
  const navigate = useNavigate();
  const { register, loginWithGoogle } = useAuth();

  const [fullNameErrorMessage, setFullNameErrorMessage] = useState("");
  const [usernameErrorMessage, setUsernameErrorMessage] = useState("");
  const [emailErrorMessage, setEmailErrorMessage] = useState("");
  const [passwordErrorMessage, setPasswordErrorMessage] = useState("");
  const [confirmPasswordErrorMessage, setConfirmPasswordErrorMessage] = useState("");

  const handleRegister = async ({ fullName, username, email, password, confirmPassword }) => {
    try {
      await register(fullName, username, email, password, confirmPassword);
      navigate("/home");
    } catch (err) {
      // Check if error has validation errors structure
      if (err.errors) {
        setFullNameErrorMessage(err.errors.fullName || "");
        setUsernameErrorMessage(err.errors.username || "");
        setEmailErrorMessage(err.errors.email || "");
        setPasswordErrorMessage(err.errors.password || "");
        setConfirmPasswordErrorMessage(err.errors.confirmPassword || "");
      } else {
        // Handle generic error message (e.g., 409 Conflict)
        const errorMsg = err.message || "Error al registrarse";
        
        // If it's a duplicate user error, set username error
        if (err.status === 409 || errorMsg.includes("ya existe")) {
          setUsernameErrorMessage(errorMsg);
        } else {
          // Show as a general error on all fields or as an alert
          setEmailErrorMessage(errorMsg);
        }
      }
    }
  };

  const handleLoginRedirect = async () => {
    navigate("/login");
  };

  return (
    <div className="register-container">
      <h2 className="register-title">Crear Cuenta</h2>
      <p className="register-subtitle">Únete a Asaltamontes Female 💖</p>

      <RegisterForm onSubmit={handleRegister} fullNameErrorMessage={fullNameErrorMessage} usernameErrorMessage={usernameErrorMessage} emailErrorMessage={emailErrorMessage} passwordErrorMessage={passwordErrorMessage} confirmPasswordErrorMessage={confirmPasswordErrorMessage} />

      <div className="login-divider">
        <span>o</span>
      </div>

      <div className="google-login-wrapper">
        <GoogleLoginButton onLoginSuccess={loginWithGoogle} />
      </div>
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
