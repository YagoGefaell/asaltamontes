import "./CommunityProfileDetail.css";
import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import Loading from "../../shared/components/Loading";
import FormError from "../../shared/forms/FormError";
import { formatters } from "../../utils/helpers";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";

function CommunityProfile() {
  const { communityName } = useParams();
  const navigate = useNavigate();
  const { community, loading, error } = useCommunityDetail(communityName); // Eliminamos refetch de aquí ya que no lo usaremos
  
  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const [memberAvatarErrors, setMemberAvatarErrors] = useState({});

  useEffect(() => {
    if (!communityName) {
      navigate("/communities");
    }
  }, [communityName, navigate]);

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
          <button
            className="back-btn"
            onClick={() => navigate(-1)}
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
        </div>
        <FormError message={error || "Comunidad no encontrada"} />
      </div>
    );
  }

  return (
    <div className="community-profile-page">
      {/* Header */}
      <div className="profile-header other-profile">
        <button
          className="back-btn"
          onClick={() => navigate(-1)}
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
      </div>

      {/* Community Avatar */}
      <div className="profile-avatar-container">
        {!avatarLoadError ? (
          <img
            src={formatters.avatar(community.imageUrl, community.name, community.name)}
            alt={community.name}
            className="profile-avatar"
            onError={() => setAvatarLoadError(true)}
          />
        ) : (
          <div className="profile-avatar profile-avatar-fallback" aria-hidden="true">
            {formatters.getInitial(community.name, community.name)}
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
          <div className="stat">
            <strong>{community.membersCount || 0}</strong>
            <span className="label">Miembros</span>
          </div>
        </div>

        <div className="profile-actions">
          <CommunityJoinButton
            communityId={community.id}
            initialIsMember={community.isMember || false}
            joinLabel="Unirse"
            memberLabel="Miembro"
            /* Quitamos la prop onToggle para evitar que se disparen llamadas 
               asíncronas paralelas que pisen el estado del botón */
          />
        </div>
      </div>

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
                      <div className="member-avatar-placeholder" aria-hidden="true">
                        {formatters.getInitial(member.username, member.username)}
                      </div>
                    )}
                  </div>
                  <p className="member-username">@{member.username}</p>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

export default CommunityProfile;