// src/shared/components/Button.jsx
import React from "react";
import "./Button.css";

function Button({ children, variant = "primary", type = "button", ...props }) {
  const className = `btn ${variant}`;
  return (
    <button type={type} className={className} {...props}>
      {children}
    </button>
  );
}

export default Button;