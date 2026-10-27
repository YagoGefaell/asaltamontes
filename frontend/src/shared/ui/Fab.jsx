import "./Fab.css";

/**
 * Botón flotante de acción (abajo a la derecha, por encima de la barra inferior).
 * Solo icono: `label` es obligatorio (aria-label).
 */
export default function Fab({ icon, label, onClick, className = "", ...props }) {
  return (
    <button
      type="button"
      className={["fab", className].filter(Boolean).join(" ")}
      aria-label={label}
      onClick={onClick}
      {...props}
    >
      {icon}
    </button>
  );
}
