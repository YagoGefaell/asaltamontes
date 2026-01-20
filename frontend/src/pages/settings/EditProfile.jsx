import "./EditProfile.css";
import { useState, useEffect } from "react";
import Input from "../../shared/components/Input.jsx";
import Button from "../../shared/components/Button.jsx";
import ToggleSwitch from "../../shared/components/ToggleSwitch.jsx";
import { useUserProfile } from "../../features/users/hooks/useUserProfile";

export default function EditProfile() {
  const { userProfile, loading, updateUserProfile } = useUserProfile();

  const [form, setForm] = useState({
    fullName: "",
    username: "",
    bio: "",
    city: "",
    profilePictureUrl: "",
    isPublicProfile: false,
  });

  const [fullNameError, setFullNameError] = useState("");
  const [usernameError, setUsernameError] = useState("");
  const [bioError, setBioError] = useState("");
  const [cityError, setCityError] = useState("");
  const [profilePictureError, setProfilePictureError] = useState("");

  // ⭐ INICIALIZAR FORMULARIO CORRECTAMENTE
  useEffect(() => {
    if (!loading && userProfile) {
      setForm({
        fullName: userProfile.fullName || "",
        username: userProfile.username || "",
        bio: userProfile.bio || "",
        city: userProfile.city || "",
        profilePictureUrl:
          userProfile.profilePictureUrl ||
          `https://api.dicebear.com/7.x/avataaars/svg?seed=${userProfile.username}`,
        isPublicProfile: userProfile.isPublicProfile || false,
      });
    }
  }, [userProfile]);

  if (loading || !userProfile) return null;

  // Capitalizar nombre
  const capitalizeWords = (text = "") =>
    text
      .toLowerCase()
      .trim()
      .split(" ")
      .filter(Boolean)
      .map((word) => word[0].toUpperCase() + word.slice(1))
      .join(" ");

  // Actualizar campos
  const handleChange = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  // Enviar cambios
  const handleSubmit = async (e) => {
    e.preventDefault();

    const payload = {};

    if (form.fullName !== userProfile.fullName)
      payload.fullName = capitalizeWords(form.fullName);

    if (form.bio !== userProfile.bio) payload.bio = form.bio.trim();

    if (form.city !== userProfile.city) payload.city = form.city.trim();

    if (form.profilePictureUrl !== userProfile.profilePictureUrl)
      payload.profilePictureUrl = form.profilePictureUrl.trim();

    if (form.isPublicProfile !== userProfile.isPublicProfile)
      payload.isPublicProfile = form.isPublicProfile;

    try {
      await updateUserProfile(payload);
    } catch (err) {
      let parsed;
      parsed = JSON.parse(err.message);
      setFullNameError(parsed.fullName || "");
      setUsernameError(parsed.username || "");
      setBioError(parsed.bio || "");
      setCityError(parsed.city || "");
      setProfilePictureError(parsed.profilePictureUrl || "");
    }
  };

  return (
    <form onSubmit={handleSubmit} className="edit-profile-form">
      <div className="avatar-section">
        <img
          src={form.profilePictureUrl || "/default-avatar.png"}
          alt="Foto de perfil"
          className="avatar-image"
        />
      </div>

      <Input
        label="URL de la foto de perfil"
        value={form.profilePictureUrl}
        onChange={(e) => handleChange("profilePictureUrl", e.target.value)}
        error={profilePictureError}
      />

      <Input
        label="Nombre completo"
        value={form.fullName}
        onChange={(e) => handleChange("fullName", e.target.value)}
        error={fullNameError}
      />

      <Input
        label="Nombre de usuario"
        value={form.username}
        onChange={(e) => handleChange("username", e.target.value)}
        error={usernameError}
      />

      <Input
        label="Biografía"
        value={form.bio}
        onChange={(e) => handleChange("bio", e.target.value)}
        as="textarea"
        error={bioError}
      />

      <Input
        label="Ciudad"
        value={form.city}
        onChange={(e) => handleChange("city", e.target.value)}
        error={cityError}
      />

      <ToggleSwitch
        label="Perfil público"
        checked={form.isPublicProfile}
        onChange={(e) => handleChange("isPublicProfile", e.target.checked)}
      />

      <Button type="submit">GUARDAR CAMBIOS</Button>
    </form>
  );
}
