// src/shared/components/Button.jsx
import React from "react";
import "./Button.css";

function Button({ 
  children, 
  variant = "primary", 
  type = "button", 
  loading = false,
  disabled = false,
  ...props 
}) {
  const className = `btn ${variant}`;
  const isDisabled = disabled || loading;
  
  return (
    <button 
      type={type} 
      className={className} 
      disabled={isDisabled}
      {...props}
    >
      {loading ? "Cargando..." : children}
    </button>
  );
}

export default Button;