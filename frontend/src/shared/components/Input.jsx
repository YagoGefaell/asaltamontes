// src/shared/components/Input.jsx
import { useState } from "react";
import FormError from "./FormError";
import "./Input.css";

export default function Input({
  label,
  type = "text",
  value,
  onChange,
  error,
  placeholder,
  ...props
}) {
  const [showPassword, setShowPassword] = useState(false);

  // Bloquear espacios si es password
  const handleKeyDown = (e) => {
    if (e.key === " ") {
      e.preventDefault();
    }
  };

  return (
    <div className="input-group">
      {label && <label>{label}</label>}
      <div className="input-wrapper">
        <input
          type={showPassword ? "text" : type}
          value={value}
          onChange={onChange}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          {...props}
        />

        {type === "password" && (
          <button
            type="button"
            className="toggle-password-btn"
            onClick={() => setShowPassword(!showPassword)}
            aria-label={
              showPassword ? "Ocultar contraseña" : "Mostrar contraseña"
            }
          >
            {showPassword ? (
              // Icono ojo abierto
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                width="20"
                height="20"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5C21.27 7.61 17 4.5 12 4.5z"
                />
                <circle
                  cx="12"
                  cy="12"
                  r="3"
                  stroke="currentColor"
                  strokeWidth={2}
                  fill="none"
                />
              </svg>
            ) : (
              // Icono ojo cerrado
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                width="20"
                height="20"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M17.94 17.94C16.22 19.25 14.16 20 12 20c-5 0-9.27-3.11-11-7.5.92-2.33 2.46-4.29 4.38-5.7M9.88 9.88a3 3 0 104.24 4.24"
                />
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M3 3l18 18"
                />
              </svg>
            )}
          </button>
        )}
      </div>
      <FormError message={error} />
    </div>
  );
}
