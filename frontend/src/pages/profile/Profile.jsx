import "./Profile.css";
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { useUserProfile } from "../../features/users/hooks/useUserProfile";
import FollowUserButton from "../../shared/components/FollowUserButton";
import Button from "../../shared/forms/Button";
import Loading from "../../shared/components/Loading";
import { formatters } from "../../utils/helpers";

function Profile({ 
  userData = null, 
  isOwnProfile = true, 
  loading = false, 
  error = null, 
  onBack = null
}) {
  const { currentUser, setCurrentUser } = useAuth();
  const { userProfile, loading: contextLoading } = useUserProfile();
  const navigate = useNavigate();
  const [openChatLoading, setOpenChatLoading] = useState(false);
  const [avatarLoadError, setAvatarLoadError] = useState(false);

  // Use props data if provided, otherwise use context data
  const profileData = userData || userProfile;
  const isLoading = loading || contextLoading;

  // Initialize currentUser in AuthContext when own profile loads
  useEffect(() => {
    if (isOwnProfile && profileData && !currentUser) {
      setCurrentUser({
        username: profileData.username,
        followersCount: profileData.followersCount || 0,
        followingCount: profileData.followingCount || 0,
      });
    }
  }, [isOwnProfile, profileData, currentUser, setCurrentUser]);

  const handleOpenChat = async () => {
    if (!profileData?.username) return;

    setOpenChatLoading(true);

    try {
      navigate("/messages", {
        state: {
          selectedConversation: {
            username: profileData.username,
            avatarUrl: profileData.profilePictureUrl || null,
          },
        },
      });
    } finally {
      setOpenChatLoading(false);
    }
  };
  
  if (isLoading) return <Loading />;
  
  if (error) {
    return (
      <div className="profile-page">
        <div className={`profile-header ${!isOwnProfile ? 'other-profile' : ''}`}>
          {onBack && (
            <button
              className="back-btn"
              onClick={onBack}
              aria-label="Volver atrás"
            >
              <svg
                className="back-icon"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <polyline points="15 18 9 12 15 6" />
              </svg>
            </button>
          )}
          {isOwnProfile && <h2 className="profile-title">Perfil</h2>}
        </div>
        <div className="error-message">
          <p>{error}</p>
          <Button onClick={() => navigate("/search")}>Volver a la búsqueda</Button>
        </div>
      </div>
    );
  }
  
  if (!profileData) {
    return isOwnProfile ? (
      <p className="no-profile">No hay perfil disponible.</p>
    ) : null;
  }

  const {
    username = "usuario",
    fullName = "Nombre completo",
    bio = isOwnProfile ? "Agrega una bio breve..." : "Sin biografía",
    city = "",
    profilePictureUrl,
    runningLevel = "BEGINNER",
    isVerified = false,
    followersCount = 0,
    followingCount = 0,
  } = profileData;

  const displayFollowersCount = isOwnProfile && currentUser 
    ? currentUser.followersCount 
    : (followersCount || 0);
  
  const displayFollowingCount = isOwnProfile && currentUser 
    ? currentUser.followingCount 
    : (followingCount || 0);

  const levelLabels = {
    BEGINNER: "Principiante",
    INTERMEDIATE: "Intermedio", 
    ADVANCED: "Avanzado",
    EXPERT: "Experto"
  };

  const avatarUrl = formatters.avatar(profilePictureUrl, fullName, username);
  const avatarInitial = formatters.getInitial(fullName, username);
  return (
    <div className="profile-page">
      {/* Barra superior con icono de ajustes o botón volver */}
      <div className={`profile-header ${!isOwnProfile ? 'other-profile' : ''}`}>
        {onBack && (
          <button
            className="back-btn"
            onClick={onBack}
            aria-label="Volver atrás"
          >
            <svg
              className="back-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
        )}
        {isOwnProfile && <h2 className="profile-title">Perfil</h2>}
        {isOwnProfile && (
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
        )}
      </div>

      <div className="profile-avatar-container">
        {!avatarLoadError ? (
          <img
            src={avatarUrl}
            alt={`${username}'s avatar`}
            className="profile-avatar"
            onError={() => setAvatarLoadError(true)}
          />
        ) : (
          <div className="profile-avatar profile-avatar-fallback" aria-hidden="true">
            {avatarInitial}
          </div>
        )}
        {isVerified && (
          <div className="verified-badge">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
        )}
      </div>

      <div className="profile-main">
        <h2 className="username">@{username}</h2>
        <p className="full-name">{fullName}</p>
        <p className="bio">{bio}</p>
        
        {city && (
          <p className="city">
            <span className="city-icon">📍</span>
            {city}
          </p>
        )}
        
        <div className="running-level">
          <span className="level-label">Nivel: </span>
          <span className={`level-badge level-${runningLevel.toLowerCase()}`}>
            {levelLabels[runningLevel] || runningLevel}
          </span>
        </div>

        <div className="profile-stats">
          <button
            className="stat stat-btn"
            onClick={() => isOwnProfile ? navigate("/me/followers") : navigate("/users/" + username + "/followers")}
            aria-label="Ver seguidores"
          >
            <span className="count">{displayFollowersCount}</span>
            <span className="label">Seguidores</span>
          </button>
          <button
            className="stat stat-btn"
            onClick={() => isOwnProfile ? navigate("/me/following") : navigate("/users/" + username + "/following")}
            aria-label="Ver usuarios que sigo"
          >
            <span className="count">{displayFollowingCount}</span>
            <span className="label">Siguiendo</span>
          </button>
        </div>

        {!isOwnProfile && (
          <div className="profile-actions">
            <FollowUserButton
              userId={profileData.username}
              initialIsFollowing={profileData.isFollowing || false}
            />
            <Button
              variant="primary"
              onClick={handleOpenChat}
              loading={openChatLoading}
              disabled={openChatLoading}
            >
              {openChatLoading ? "Cargando..." : "Abrir chat"}
            </Button>
          </div>
        )}

        {isOwnProfile && (
          <div className="profile-extra">
            <p className="placeholder-text">
              Aquí podrías agregar logros, links, badges o información adicional
              del usuario.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

export default Profile;
