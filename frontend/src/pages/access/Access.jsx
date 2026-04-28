import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth.js";
import { GoogleLoginButton } from '../../shared/components/GoogleLoginButton.jsx';
import logo from "../../assets/logo01.png";
import "./Access.css";

const Access = () => {
  const { loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="access-container">
      <header className="access-header">
        <h1 className="access-title">Asaltamontes Female</h1>
        <p className="access-subtitle">
          Conecta, comparte y crece con otras mujeres como tú.
        </p>
      </header>

      <div className="access-illustration">
        <img
          src={logo}
          alt="Women community"
        />
      </div>

      <div className="access-buttons">
        <button
          className="btn-primary"
          onClick={() => navigate("/login")}
        >
          Iniciar Sesión
        </button>
        <button
          className="btn-secondary"
          onClick={() => navigate("/register")}
        >
          Crear Cuenta
        </button>
      </div>
      <div className="login-divider">
        <span>o</span>
      </div>

      <div className="google-login-wrapper">
        <GoogleLoginButton onLoginSuccess={loginWithGoogle} />
      </div>
    </div>
  );
};

export default Access;
