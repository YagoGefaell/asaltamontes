import "./Chip.css";

/** Chip de filtro. Con `selected` actúa como interruptor (aria-pressed). */
export function Chip({ children, selected = false, onClick, className = "", ...props }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      className={["chip", selected ? "chip--selected" : "", className].filter(Boolean).join(" ")}
      onClick={onClick}
      {...props}
    >
      {children}
    </button>
  );
}

/** Una sola fila de chips deslizable horizontalmente (~40 px de alto). */
export function ChipRow({ children, label, className = "" }) {
  return (
    <div className={["chip-row", className].filter(Boolean).join(" ")} role="group" aria-label={label}>
      {children}
    </div>
  );
}

export default Chip;
