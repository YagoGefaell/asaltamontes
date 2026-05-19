import "./CommunityProfile.css";
import { useState } from "react";
import { useAuth } from "../../features/auth/hooks/useAuth";
import Button from "../../shared/forms/Button";
import Loading from "../../shared/components/Loading";
import FormError from "../../shared/forms/FormError";
import { formatters } from "../../utils/helpers";
import { joinCommunityRequest, leaveCommunityRequest } from "../../features/communities/services/communities.service";

function CommunityProfileDetail({ 
  community = null, 
  loading = false, 
  error = null,
  isMember = false,
  onBack = null,
  onRefetch = null
}) {
  const [joinLoading, setJoinLoading] = useState(false);
  const [joinError, setJoinError] = useState(null);
  const [isUserMember, setIsUserMember] = useState(isMember);
  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const [memberAvatarErrors, setMemberAvatarErrors] = useState({});

  const handleJoinCommunity = async () => {
    if (!community?.id) return;
    
    setJoinLoading(true);
    setJoinError(null);

    try {
      await joinCommunityRequest(community.id);
      setIsUserMember(true);
      if (onRefetch) onRefetch();
    } catch (err) {
      setJoinError(err.message || "Error al unirse a la comunidad");
      console.error("Error joining community:", err);
    } finally {
      setJoinLoading(false);
    }
  };

  const handleLeaveCommunity = async () => {
    if (!community?.id) return;
    
    setJoinLoading(true);
    setJoinError(null);

    try {
      await leaveCommunityRequest(community.id);
      setIsUserMember(false);
      if (onRefetch) onRefetch();
    } catch (err) {
      setJoinError(err.message || "Error al salir de la comunidad");
      console.error("Error leaving community:", err);
    } finally {
      setJoinLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="community-profile-page">
        <Loading />
      </div>
    );
  }

  if (error || !community) {
    return (
      <div className="community-profile-page">
        <div className="profile-header other-profile">
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
        </div>
        {(error || !community) && (
          <FormError message={error || "Comunidad no encontrada"} />
        )}
      </div>
    );
  }

  return (
    <div className="community-profile-page">
      {/* Header similar al perfil de usuario */}
      <div className="profile-header other-profile">
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
      </div>

      {/* Community Avatar */}
      <div className="profile-avatar-container">
        {!avatarLoadError && community.imageUrl ? (
          <img
            src={community.imageUrl}
            alt={community.name}
            className="profile-avatar"
            onError={() => setAvatarLoadError(true)}
          />
        ) : (
          <div className="profile-avatar profile-avatar-fallback" aria-hidden="true">
            {community.name?.charAt(0)?.toUpperCase()}
          </div>
        )}
      </div>

      {/* Community Info */}
      <div className="profile-main">
        <h2 className="username">{community.name}</h2>
        
        {community.description && (
          <p className="bio">{community.description}</p>
        )}

        <div className="profile-stats">
          <div className="stat clickable">
            <strong>{community.membersCount || 0}</strong>
            <span className="label">Miembros</span>
          </div>
        </div>

        {/* Join/Leave Actions */}
        <div className="profile-actions">
          {!isUserMember ? (
            <Button 
              onClick={handleJoinCommunity}
              disabled={joinLoading}
            >
              {joinLoading ? "Uniéndose..." : "Unirse"}
            </Button>
          ) : (
            <Button 
              onClick={handleLeaveCommunity}
              disabled={joinLoading}
              variant="secondary"
            >
              {joinLoading ? "Saliendo..." : "Salir"}
            </Button>
          )}
        </div>

        {/* Error Message */}
        {joinError && <FormError message={joinError} />}
      </div>

      {/* Members Section */}
      {community.members && community.members.length > 0 && (
        <div className="members-section">
          <h3 className="members-title">Miembros ({community.members.length})</h3>
          
          <div className="members-grid">
            {community.members.map((member) => {
              const memberAvatarUrl = formatters.avatar(member.profilePictureUrl, member.username, member.username);
              const hasError = memberAvatarErrors[member.id];
              
              return (
                <div key={member.id} className="member-card">
                  <div className="member-avatar">
                    {!hasError ? (
                      <img 
                        src={memberAvatarUrl} 
                        alt={member.username}
                        onError={() => setMemberAvatarErrors(prev => ({ ...prev, [member.id]: true }))}
                      />
                    ) : (
                      <div className="member-avatar-placeholder">
                        {member.username?.charAt(0)?.toUpperCase()}
                      </div>
                    )}
                  </div>
                  <div className="member-info">
                    <p className="member-username">{member.username}</p>
                    <p className="member-role">{member.role}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

export default CommunityProfileDetail;
