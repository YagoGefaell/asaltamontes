import './FormError.css';

export default function FormError({ message }) {
  if (!message) return null; // No renderiza nada si no hay mensaje

  return (
    <p className="form-error">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        className="error-icon"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        width="16"
        height="16"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M12 8v4m0 4h.01M12 2a10 10 0 100 20 10 10 0 000-20z"
        />
      </svg>
      {message}
    </p>
  );
}
