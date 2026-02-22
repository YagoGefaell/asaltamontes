import "./SettingsMenu.css";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";

export default function SettingsMenu() {
  const navigate = useNavigate();
  const { logout } = useAuth();

  const handleLogout = async () => {
    if (window.confirm("¿Estás segura de que quieres cerrar sesión?")) {
      try {
        await logout();
        navigate("/access");
      } catch (error) {
        console.error("Error al cerrar sesión:", error);
      }
    }
  };

  const sections = [
    {
      title: "Cuenta",
      items: [
        { label: "Preferencias", path: "/settings/preferences" },
        { label: "Perfil", path: "/settings/profile" },
        { label: "Notificaciones", path: "/settings/notifications" },
        { label: "Cursos", path: "/settings/courses" },
        { label: "Score en LinkedIn", path: "/settings/linkedin" },
        { label: "Duolingo for Schools", path: "/settings/schools" },
        { label: "Redes sociales", path: "/settings/social" },
        { label: "Ajustes de privacidad", path: "/settings/privacy" },
      ],
    },
    {
      title: "Suscripción",
      items: [{ label: "Escoge un plan", path: "/settings/plan" }],
    },
    {
      title: "Soporte",
      items: [{ label: "Centro de ayuda", path: "/settings/help" }],
    },
    {
      title: "Sesión",
      items: [{ label: "Cerrar sesión", action: handleLogout, isAction: true }],
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
    </div>
  );
}
