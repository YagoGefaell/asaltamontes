import "./Profile.css";
import { useContext } from "react";
import { useNavigate } from "react-router-dom";
import { useUserProfile } from "../features/users/hooks/useUserProfile";

function Profile() {
  const { userProfile, loading } = useUserProfile();
  const navigate = useNavigate();

  if (loading) return <p className="loading">Cargando perfil...</p>;
  if (!userProfile) return <p className="no-profile">No hay perfil disponible.</p>;

  const {
    username = "usuario",
    fullName = "Nombre completo",
    bio = "Agrega una bio breve...",
    profilePictureUrl,
    followersCount = 0,
    followingCount = 0,
  } = userProfile;

  return (
    <div className="profile-page">
      <div className="profile-avatar-container">
        <img
          src={profilePictureUrl || `https://api.dicebear.com/7.x/avataaars/svg?seed=${username}`}
          alt={`${username}'s avatar`}
          className="profile-avatar"
        />
      </div>

      <div className="profile-main">
        <h2 className="username">@{username}</h2>
        <p className="full-name">{fullName}</p>
        <p className="bio">{bio}</p>

        <div className="profile-stats">
          <div className="stat">
            <span className="count">{followersCount}</span>
            <span className="label">Seguidores</span>
          </div>
          <div className="stat">
            <span className="count">{followingCount}</span>
            <span className="label">Siguiendo</span>
          </div>
        </div>

        <div className="profile-actions">
          <button className="edit-btn" onClick={() => navigate("/me/edit")}>Editar perfil</button>
          <button className="settings-btn">Ajustes</button>
        </div>

        <div className="profile-extra">
          <p className="placeholder-text">
            Aquí podrías agregar logros, links, badges o información adicional del usuario.
          </p>
        </div>
      </div>
    </div>
  );
}

export default Profile;
