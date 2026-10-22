import "./Skeleton.css";

/**
 * Marcador de carga. `circle` para avatares; `width`/`height` aceptan cualquier valor CSS.
 */
export default function Skeleton({ width = "100%", height = "1rem", circle = false, className = "" }) {
  const classes = ["skeleton", circle ? "skeleton--circle" : "", className].filter(Boolean).join(" ");
  return <span className={classes} style={{ width, height }} aria-hidden="true" />;
}
