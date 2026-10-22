import "./EmptyState.css";

/**
 * Estado vacío: mensaje claro y, opcionalmente, una acción.
 */
export default function EmptyState({ title, description, action, icon, className = "" }) {
  return (
    <div className={["empty-state", className].filter(Boolean).join(" ")}>
      {icon && <div className="empty-state__icon" aria-hidden="true">{icon}</div>}
      <p className="empty-state__title">{title}</p>
      {description && <p className="empty-state__description">{description}</p>}
      {action && <div className="empty-state__action">{action}</div>}
    </div>
  );
}
