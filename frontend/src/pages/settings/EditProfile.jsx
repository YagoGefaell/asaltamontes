import "./EditProfile.css";
import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import Input from "../../shared/forms/Input.jsx";
import Button from "../../shared/forms/Button.jsx";
import ToggleSwitch from "../../shared/ui/ToggleSwitch.jsx";
import AvatarUploader from "../../shared/ui/AvatarUploader.jsx";
import FormError from "../../shared/forms/FormError.jsx";
import { useUserProfile } from "../../features/users/hooks/useUserProfile";

export default function EditProfile() {
  const { userProfile, loading, updateUserProfile } = useUserProfile();
  const navigate = useNavigate();

  const [form, setForm] = useState(null);

  const [fullNameError, setFullNameError] = useState("");
  const [usernameError, setUsernameError] = useState("");
  const [bioError, setBioError] = useState("");
  const [cityError, setCityError] = useState("");
  const [profilePictureError, setProfilePictureError] = useState("");
  const [uploadingPicture, setUploadingPicture] = useState(false);

  const initialForm = useMemo(() => {
    if (!loading && userProfile) {
      return {
        fullName: userProfile.fullName || "",
        username: userProfile.username || "",
        bio: userProfile.bio || "",
        city: userProfile.city || "",
        profilePictureUrl: userProfile.profilePictureUrl || "",
        avatarMediaId: null,
        isPublicProfile: userProfile.isPublicProfile || false,
      };
    }
    return null;
  }, [userProfile, loading]);

  // Sync form state with userProfile only on first load
  useMemo(() => {
    if (initialForm && !form) {
      setForm(initialForm);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialForm]);

  if (loading || !userProfile || !form) return null;

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

    if (form.username !== userProfile.username)
      payload.username = form.username.trim();

    if (form.bio !== userProfile.bio) payload.bio = form.bio.trim();

    if (form.city !== userProfile.city) payload.city = form.city.trim();

    // A new picture is sent by the id of the uploaded media; "" removes the current one
    if (form.avatarMediaId) payload.avatarMediaId = form.avatarMediaId;
    else if (!form.profilePictureUrl && userProfile.profilePictureUrl)
      payload.profilePictureUrl = "";

    if (form.isPublicProfile !== userProfile.isPublicProfile)
      payload.isPublicProfile = form.isPublicProfile;

    try {
      await updateUserProfile(payload);
      navigate("/me");
    } catch (err) {
      let parsed = {};

      try {
        parsed = JSON.parse(err.message);
      } catch {
        console.error("Respuesta no JSON:", err.message);
      }

      setFullNameError(parsed.fullName || "");
      setUsernameError(parsed.username || "");
      setBioError(parsed.bio || "");
      setCityError(parsed.city || "");
      setProfilePictureError(parsed.avatarMediaId || parsed.profilePictureUrl || "");
    }

  };

  return (
    <form onSubmit={handleSubmit} className="edit-profile-form">
      <div className="avatar-section">
        <AvatarUploader
          value={form.profilePictureUrl}
          onChange={(url, mediaId) =>
            setForm((prev) => ({ ...prev, profilePictureUrl: url, avatarMediaId: mediaId || null }))
          }
          onUploadingChange={setUploadingPicture}
          fullName={form.fullName}
          username={form.username}
        />
        <FormError message={profilePictureError} />
      </div>

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

      <Button type="submit" disabled={uploadingPicture}>GUARDAR CAMBIOS</Button>
    </form>
  );
}
