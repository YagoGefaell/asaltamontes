import { useState } from "react";
import "./Avatar.css";

const SIZES = { sm: 32, md: 44, lg: 64, xl: 96 };

function initialOf(name) {
  const trimmed = (name || "").trim();
  return trimmed ? trimmed.charAt(0).toUpperCase() : "?";
}

/**
 * Imagen de perfil con iniciales de respaldo si no hay imagen o falla la carga.
 * `size`: sm | md | lg | xl, o un número de píxeles para un caso puntual.
 */
export default function Avatar({ src, name, size = "md", className = "" }) {
  const [failedSrc, setFailedSrc] = useState(null);
  const showImage = Boolean(src) && failedSrc !== src;
  const px = typeof size === "number" ? size : SIZES[size] ?? SIZES.md;
  const classes = ["avatar", className].filter(Boolean).join(" ");

  if (showImage) {
    return (
      <img
        src={src}
        alt={name || ""}
        className={classes}
        style={{ width: px, height: px }}
        onError={() => setFailedSrc(src)}
      />
    );
  }

  return (
    <div
      className={`${classes} avatar-fallback`}
      style={{ width: px, height: px, fontSize: px * 0.4 }}
      role="img"
      aria-label={name || "Sin imagen"}
    >
      {initialOf(name)}
    </div>
  );
}
