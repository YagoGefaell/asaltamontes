import { formatters } from "../../utils/helpers";
import { MEDIA_PURPOSE } from "../../features/media/services/media.service.js";
import ImageUploader from "./ImageUploader.jsx";

/** Foto de perfil: un `ImageUploader` con el propósito AVATAR y el avatar por defecto como imagen de reserva. */
export default function AvatarUploader({ value, onChange, fullName, username, ...rest }) {
  return (
    <ImageUploader
      {...rest}
      value={value}
      onChange={onChange}
      purpose={MEDIA_PURPOSE.AVATAR}
      label="Foto de perfil"
      fallbackSrc={formatters.avatar(null, fullName, username)}
      shape="round"
    />
  );
}
