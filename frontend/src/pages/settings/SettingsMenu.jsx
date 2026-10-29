import "./SettingsMenu.css";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import FormError from "../../shared/forms/FormError";
import { logoutEverywhereRequest } from "../../features/auth/services/auth.service";

export default function SettingsMenu() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [error, setError] = useState("");

  const handleLogout = async () => {
    setError("");
    try {
      await logout();
      navigate("/access");
    } catch (err) {
      console.error("Error al cerrar sesión:", err);
      setError("No se ha podido cerrar la sesión. Inténtalo de nuevo.");
    }
  };

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

  const sections = [
    {
      title: "Cuenta",
      items: [
        { label: "Perfil", path: "/settings/profile" },
        { label: "Notificaciones", path: "/settings/notifications" },
        { label: "Ajustes de privacidad", path: "/settings/privacy" },
      ],
    },
    {
      title: "Sesión",
      items: [
        { label: "Cerrar sesión", action: handleLogout, isAction: true },
        { label: "Cerrar sesión en todos los dispositivos", action: handleLogoutEverywhere, isAction: true },
      ],
    },
  ];

  return (
    <div className="settings-menu">
      {sections.map((section) => (
        <div key={section.title} className="settings-section">
          <h3 className="section-title">{section.title}</h3>
          <ul className="section-list">
            {section.items.map((item) => (
              <li
                key={item.label}
                className={`settings-item ${item.isAction ? 'logout-item' : ''}`}
                onClick={() => item.isAction ? item.action() : navigate(item.path)}
              >
                <span>{item.label}</span>
                <span className="arrow">›</span>
              </li>
            ))}
          </ul>
        </div>
      ))}
      <FormError message={error} />
    </div>
  );
}
