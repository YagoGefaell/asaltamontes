import { useNavigate } from "react-router-dom";
import "./SettingsLayout.css";

export default function SettingsLayout({
  title = "Ajustes",
  backTo = "/",
  children,
}) {
  const navigate = useNavigate();

  return (
    <div className="settings-container">
      <header className="settings-header">
        <button
          className="back-btn"
          onClick={() => navigate(backTo)}
          aria-label="Volver"
        >
          <svg
            className="back-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>

        <h1 className="settings-title">{title}</h1>
      </header>

      <main className="settings-content">{children}</main>
    </div>
  );
}
