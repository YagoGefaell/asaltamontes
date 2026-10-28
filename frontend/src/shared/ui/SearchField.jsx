import { forwardRef } from "react";
import { FaSearch, FaTimes } from "react-icons/fa";
import "./SearchField.css";

/**
 * Campo de búsqueda redondeado (lupa + borrar), el mismo en Search y en las listas de seguidoras.
 * Es un `<form role="search">`: Enter llama a `onSubmit` si se pasa. `onClear` deja el texto vacío;
 * el resto de props (onFocus, onKeyDown…) van al `<input>`.
 */
const SearchField = forwardRef(function SearchField(
  { value, onChange, onClear, onSubmit, label = "Buscar", placeholder = label, className = "", ...inputProps },
  ref,
) {
  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit?.(e);
  };

  return (
    <form className={["search-field", className].filter(Boolean).join(" ")} role="search" onSubmit={handleSubmit}>
      <FaSearch className="search-field__icon" aria-hidden="true" />
      <input
        ref={ref}
        type="search"
        enterKeyHint="search"
        placeholder={placeholder}
        aria-label={label}
        autoComplete="off"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        {...inputProps}
      />
      {value && (
        <button type="button" className="search-field__clear" aria-label="Borrar búsqueda" onClick={onClear}>
          <FaTimes aria-hidden="true" />
        </button>
      )}
    </form>
  );
});

export default SearchField;
