import { useNavigate } from "react-router-dom";
import "./Access.css";
import logo from "../../assets/logo01.png";

const Access = () => {
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
    </div>
  );
};

export default Access;
