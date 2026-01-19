import "./EditProfile.css";
import { useState, useEffect } from "react";
import { useUserProfile } from "../features/users/hooks/useUserProfile";
import Button from "../shared/components/Button";
import Input from "../shared/components/Input";
import AvatarUploader from "../shared/components/AvatarUploader";
import SectionCard from "../shared/components/SectionCard";
import ToggleSwitch from "../shared/components/ToggleSwitch";

export default function EditProfile() {
  const { userProfile, loading, updateUserProfile } = useUserProfile();
  
  const [fullName, setFullName] = useState("");
  const [username, setUsername] = useState("");
  const [bio, setBio] = useState("");
  const [city, setCity] = useState("");
  const [profilePictureUrl, setProfilePictureUrl] = useState("");
  const [runningLevel, setRunningLevel] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [isPublicProfile, setIsPublicProfile] = useState(false);
  
  useEffect(() => {
    if (userProfile) {
      setFullName(userProfile.fullName || "");
      setUsername(userProfile.username || "");
      setBio(userProfile.bio || "");
      setCity(userProfile.city || "");
      setProfilePictureUrl(userProfile.profilePictureUrl || "");
      setRunningLevel(userProfile.runningLevel || "");
      setBirthDate(userProfile.birthDate || "");
      setIsPublicProfile(userProfile.isPublicProfile || false);
    }
  }, [userProfile]);
  

  if (loading || !userProfile) return null;
  
  const capitalizeWords = (text = "") =>
    text
      .toLowerCase()
      .trim()
      .split(" ")
      .filter(Boolean)
      .map((word) => word[0].toUpperCase() + word.slice(1))
      .join(" ");

  const handleSubmit = (e) => {
    e.preventDefault();

    const payload = {};

    if (fullName !== userProfile.fullName)
      payload.fullName = capitalizeWords(fullName);

    if (bio !== userProfile.bio)
      payload.bio = bio.trim();

    if (city !== userProfile.city)
      payload.city = city.trim();

    if (profilePictureUrl !== userProfile.profilePictureUrl)
      payload.profilePictureUrl = profilePictureUrl.trim();

    if (runningLevel !== userProfile.runningLevel)
      payload.runningLevel = runningLevel;

    if (isPublicProfile !== userProfile.isPublicProfile)
      payload.isPublicProfile = isPublicProfile;

    if (Object.keys(payload).length > 0) {
      updateUserProfile(payload);
    }
  };

  return (
    <div className="profile-edit-page">
      <form onSubmit={handleSubmit} className="profile-edit-form">
        <SectionCard>
          <AvatarUploader key={userProfile.profilePictureUrl} value={profilePictureUrl} onChange={setProfilePictureUrl} />
          <Input label="Nombre completo" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          <small className="username">@{username}</small>
        </SectionCard>

        <SectionCard>
          <Input
            label="Biografía"
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            placeholder="Cuéntanos sobre ti..."
            as="textarea"
          />
        </SectionCard>

        <SectionCard>
          <Input label="Ciudad" value={city} onChange={(e) => setCity(e.target.value)} />
          <Input label="Email" type="email" value={userProfile.email} disabled />
        </SectionCard>

        <SectionCard>
          <Input label="Nivel de running" as="select" value={runningLevel} onChange={(e) => setRunningLevel(e.target.value)}>
            <option value="">Selecciona nivel</option>
            <option value="BEGINNER">Principiante</option>
            <option value="INTERMEDIATE">Intermedio</option>
            <option value="ADVANCED">Avanzado</option>
          </Input>
          <Input label="Fecha de nacimiento" type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
        </SectionCard>

        <SectionCard>
          <ToggleSwitch checked={isPublicProfile} onChange={(e) => setIsPublicProfile(e.target.checked)} label="Perfil público" />
        </SectionCard>

        <Button type="submit" variant="primary">Guardar cambios</Button>
      </form>
    </div>
  );
}
