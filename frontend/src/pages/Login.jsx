import { useNavigate } from "react-router-dom";
import { useAuth } from "../features/auth/hooks/useAuth.jsx";
import LoginForm from "../features/auth/components/LoginForm.jsx"; // Importa tu formulario real
import "./Login.css";

function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleLogin = async ({ email, password }) => {
    try {
      await login(email, password);
      navigate("/home");
    } catch (err) {
      console.error("Error al iniciar sesión:", err);
      alert("Credenciales inválidas");
    }
  };

  const handleRegisterRedirect = () => {
    navigate("/register"); // Redirige a la página de registro
  };

  return (
    <div className="login-container">
      <h2 className="login-title">Iniciar Sesión</h2>
      <p className="login-subtitle">Bienvenida de nuevo 💖</p>

      {/* Aquí insertamos el LoginForm */}
      <LoginForm onSubmit={handleLogin}/>

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
