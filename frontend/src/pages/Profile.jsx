import "./Profile.css";
import { useNavigate } from "react-router-dom";
import { useUserProfile } from "../features/users/hooks/useUserProfile";

function Profile() {
  const { userProfile, loading } = useUserProfile();
  const navigate = useNavigate();

  if (loading) return <p className="loading">Cargando perfil...</p>;
  if (!userProfile)
    return <p className="no-profile">No hay perfil disponible.</p>;

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
      {/* Barra superior con icono de ajustes */}
      <div className="profile-header">
        <h2 className="profile-title">Perfil</h2>
        <button
          className="settings-icon-btn"
          onClick={() => navigate("/settings/")}
          aria-label="Ir a ajustes"
        >
          <svg
            className="settings-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="12" cy="12" r="3" />
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.09a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h.09a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
          </svg>
        </button>
      </div>

      <div className="profile-avatar-container">
        <img
          src={
            profilePictureUrl ||
            `https://api.dicebear.com/7.x/avataaars/svg?seed=${username}`
          }
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

        <div className="profile-extra">
          <p className="placeholder-text">
            Aquí podrías agregar logros, links, badges o información adicional
            del usuario.
          </p>
        </div>
      </div>
    </div>
  );
}

export default Profile;
