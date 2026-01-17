import { useState, useEffect } from "react";
import "./AvatarUploader.css";

export default function AvatarUploader({ value, onChange }) {
  const [preview, setPreview] = useState(value);

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
        src={preview || "/default-avatar.png"}
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
