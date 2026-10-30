import VerifyEmailPanel from "../../features/auth/components/VerifyEmailPanel.jsx";
import "./SettingsMenu.css";

/** Ajustes → Verificar email (T24a2): estado y reenvío del enlace. */
export default function VerifyEmailSettings() {
  return (
    <div className="settings-menu">
      <VerifyEmailPanel />
    </div>
  );
}
