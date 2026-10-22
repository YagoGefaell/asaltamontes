// src/shared/forms/Button.jsx
import "./Button.css";

const VARIANTS = ["primary", "secondary", "danger", "ghost", "outline"];

function Button({
  children,
  variant = "primary",
  type = "button",
  size = "medium",
  loading = false,
  disabled = false,
  className = "",
  ...props
}) {
  const safeVariant = VARIANTS.includes(variant) ? variant : "primary";
  const classes = ["btn", safeVariant, size, loading ? "is-loading" : "", className]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? "Cargando..." : children}
    </button>
  );
}

export default Button;
