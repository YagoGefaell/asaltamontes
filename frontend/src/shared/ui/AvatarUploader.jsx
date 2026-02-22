import { useState, useEffect } from "react";
import { formatters } from "../../utils/helpers";
import "./AvatarUploader.css";

export default function AvatarUploader({ value, onChange, fullName, username }) {
  const [preview, setPreview] = useState(value);
  
  // Generar avatar por defecto usando la función utilitaria
  const defaultAvatar = formatters.avatar(null, fullName, username);

  useEffect(() => {
    setPreview(value);
  }, [value]);

  const handleChange = (e) => {
    const url = e.target.value;
    setPreview(url);
    onChange(url);
  };

  return (
    <div className="avatar-uploader">
      <img
        src={preview || defaultAvatar}
        alt="Avatar"
        className="avatar-preview"
      />
      <input
        type="text"
        placeholder="URL de la foto"
        value={value}
        onChange={handleChange}
        className="input-text"
      />
    </div>
  );
}
