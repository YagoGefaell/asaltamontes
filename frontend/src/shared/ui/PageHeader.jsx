import { FaChevronLeft } from "react-icons/fa";
import "./PageHeader.css";

/**
 * Cabecera de pantalla de una línea: atrás opcional + título a la izquierda,
 * acciones (1-2 iconos) a la derecha.
 */
export default function PageHeader({ title, onBack, actions, className = "" }) {
  return (
    <header className={["page-header", className].filter(Boolean).join(" ")}>
      {onBack && (
        <button type="button" className="page-header__back" onClick={onBack} aria-label="Volver atrás">
          <FaChevronLeft aria-hidden="true" />
        </button>
      )}
      <h1 className="page-header__title">{title}</h1>
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}
