import "./SettingsMenu.css";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { useToast } from "../../shared/ui/Toast";
import ConfirmModal from "../../shared/ui/ConfirmModal";
import { logoutEverywhereRequest } from "../../features/auth/services/auth.service";

export default function SettingsMenu() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const toast = useToast();
  const [pending, setPending] = useState(null); // "logout" | "everywhere" | null

  const handleLogout = async () => {
    try {
      await logout();
      navigate("/access");
    } catch (error) {
      console.error("Error al cerrar sesión:", error);
    }
  };

  const handleLogoutEverywhere = async () => {
    try {
      await logoutEverywhereRequest();
      await logout();
      navigate("/access");
    } catch (error) {
      console.error("Error al cerrar las sesiones:", error);
      toast.error(error.message);
    }
  };

  const confirmPending = () => {
    const action = pending === "everywhere" ? handleLogoutEverywhere : handleLogout;
    setPending(null);
    return action();
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
      items: [
        { label: "Cerrar sesión", action: () => setPending("logout"), isAction: true },
        { label: "Cerrar sesión en todos los dispositivos", action: () => setPending("everywhere"), isAction: true },
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
      <ConfirmModal
        open={pending !== null}
        title={pending === "everywhere" ? "Cerrar sesión en todos los dispositivos" : "Cerrar sesión"}
        message={pending === "everywhere"
          ? "Se cerrará tu sesión en todos tus dispositivos, también en este. ¿Continuar?"
          : "¿Estás segura de que quieres cerrar sesión?"}
        confirmLabel="Cerrar sesión"
        destructive
        onConfirm={confirmPending}
        onCancel={() => setPending(null)}
      />
    </div>
  );
}
