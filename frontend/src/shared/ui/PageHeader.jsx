import { FaChevronLeft } from "react-icons/fa";
import "./PageHeader.css";

/**
 * Cabecera de pantalla de una línea: atrás opcional + título a la izquierda,
 * acciones (1-2 iconos) a la derecha. Todas las pantallas la usan para que
 * alto, márgenes y borde sean siempre los mismos. `children` sustituye al título
 * cuando hace falta algo más que texto (p. ej. avatar + nombre en el chat).
 */
export default function PageHeader({ title, children, onBack, backLabel = "Volver atrás", backDisabled = false, actions, className = "" }) {
  return (
    <header className={["page-header", className].filter(Boolean).join(" ")}>
      {onBack && (
        <button type="button" className="page-header__back" onClick={onBack} disabled={backDisabled} aria-label={backLabel}>
          <FaChevronLeft aria-hidden="true" />
        </button>
      )}
      {children ? <div className="page-header__content">{children}</div> : <h1 className="page-header__title">{title}</h1>}
      {actions && <div className="page-header__actions">{actions}</div>}
    </header>
  );
}
