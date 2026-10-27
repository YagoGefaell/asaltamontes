import { useRef } from "react";
import "./Tabs.css";

/**
 * Pestañas de texto deslizables (patrón ARIA tablist). Controlado:
 * `tabs` = [{ id, label }], `value` = id activo, `onChange(id)`.
 * Flechas izquierda/derecha, Inicio y Fin mueven la selección.
 */
export default function Tabs({ tabs, value, onChange, label, idPrefix = "tab", className = "" }) {
  const listRef = useRef(null);

  const onKeyDown = (event) => {
    const index = tabs.findIndex((tab) => tab.id === value);
    let next = -1;
    if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
    else if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = tabs.length - 1;
    if (next < 0) return;
    event.preventDefault();
    onChange?.(tabs[next].id);
    listRef.current?.querySelectorAll('[role="tab"]')[next]?.focus();
  };

  return (
    <div
      ref={listRef}
      className={["tabs", className].filter(Boolean).join(" ")}
      role="tablist"
      aria-label={label}
      onKeyDown={onKeyDown}
    >
      {tabs.map((tab) => {
        const selected = tab.id === value;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`${idPrefix}-${tab.id}`}
            aria-selected={selected}
            aria-controls={`${idPrefix}-panel-${tab.id}`}
            tabIndex={selected ? 0 : -1}
            className={`tabs__tab${selected ? " tabs__tab--active" : ""}`}
            onClick={() => onChange?.(tab.id)}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
