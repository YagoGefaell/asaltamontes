import "./SettingsMenu.css";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import FormError from "../../shared/forms/FormError";
import { logoutEverywhereRequest } from "../../features/auth/services/auth.service";

export default function SecuritySettings() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [error, setError] = useState("");

  // Reversible (she can log in again), so it runs at once without asking.
  const handleLogoutEverywhere = async () => {
    setError("");
    try {
      await logoutEverywhereRequest();
      await logout();
      navigate("/access");
    } catch (err) {
      console.error("Error al cerrar las sesiones:", err);
      setError(err.message);
    }
  };

  return (
    <div className="settings-menu">
      <ul className="section-list">
        <li className="settings-item" onClick={() => navigate("/settings/email")}>
          <span>Cambiar email</span>
          <span className="arrow">›</span>
        </li>
        <li className="settings-item" onClick={handleLogoutEverywhere}>
          <span>Cerrar sesión en todos los dispositivos</span>
          <span className="arrow">›</span>
        </li>
      </ul>
      <FormError message={error} />
    </div>
  );
}
