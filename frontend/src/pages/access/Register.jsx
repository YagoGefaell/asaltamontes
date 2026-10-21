import { useNavigate } from "react-router-dom";
import RegisterForm from "../../features/auth/components/RegisterForm.jsx";
import { useAuth } from "../../features/auth/hooks/useAuth.js";
import { GoogleLoginButton } from '../../shared/components/GoogleLoginButton.jsx';
import { useState } from "react";
import { REGISTER_RULES, validateForm } from "../../utils/validation.js";
import "./Register.css";

const Register = () => {
  const navigate = useNavigate();
  const { register, loginWithGoogle } = useAuth();

  // Un solo objeto para todos los errores de campo
  const [fieldErrors, setFieldErrors] = useState({});

  const handleRegister = async ({ fullName, username, email, password, confirmPassword }) => {
    // Mismas reglas que el backend: no se envía nada que vaya a rechazar
    const { isValid, errors } = validateForm({ fullName, username, email, password, confirmPassword }, REGISTER_RULES);
    setFieldErrors(errors);
    if (!isValid) return;

    try {
      await register(fullName, username, email, password, confirmPassword);
      navigate("/home");
    } catch (err) {
      // Si hay errores de validación de campos
      if (err.errors) {
        setFieldErrors({
          fullName: err.errors.fullName || "",
          username: err.errors.username || "",
          email: err.errors.email || "",
          password: err.errors.password || "",
          confirmPassword: err.errors.confirmPassword || ""
        });
      } else {
        // Error genérico
        const errorMsg = err.message || "Error al registrarse";
        if (err.status === 409 || errorMsg.includes("ya existe")) {
          setFieldErrors(errors => ({ ...errors, username: errorMsg }));
        } else {
          setFieldErrors(errors => ({ ...errors, email: errorMsg }));
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

      <RegisterForm
        onSubmit={handleRegister}
        fullNameErrorMessage={fieldErrors.fullName}
        usernameErrorMessage={fieldErrors.username}
        emailErrorMessage={fieldErrors.email}
        passwordErrorMessage={fieldErrors.password}
        confirmPasswordErrorMessage={fieldErrors.confirmPassword}
      />

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
