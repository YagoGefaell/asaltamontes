import { useNavigate } from "react-router-dom";
import PageHeader from "../ui/PageHeader";
import "./SettingsLayout.css";

export default function SettingsLayout({
  title = "Ajustes",
  backTo = "/",
  children,
}) {
  const navigate = useNavigate();

  return (
    <div className="settings-container">
      <PageHeader title={title} onBack={() => navigate(backTo)} backLabel="Volver" />

      <main className="settings-content">{children}</main>
    </div>
  );
}
