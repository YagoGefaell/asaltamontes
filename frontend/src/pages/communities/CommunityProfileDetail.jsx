import "./CommunityProfileDetail.css";
import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import Loading from "../../shared/components/Loading";
import FormError from "../../shared/forms/FormError";
import { formatters } from "../../utils/helpers";
import { useCommunityMembers } from "../../features/communities/hooks/useCommunityMembers";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import { communityPath } from "../../features/communities/utils/communityPath";
import CommunityEvents from "../../features/events/components/CommunityEvents";
import { isStaff } from "../../features/communities/utils/communityErrors";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import { useAuth } from "../../features/auth/hooks/useAuth";

function CommunityProfile() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const { community, setCommunity, loading, error } = useCommunityDetail(slug);
  
  // Only members get the list; the others see just the count
  const { members } = useCommunityMembers(community?.id, !!community?.role);

  const [avatarLoadError, setAvatarLoadError] = useState(false);
  const [memberAvatarErrors, setMemberAvatarErrors] = useState({});

  // Check if current user is the community owner
  // Owner and moderators manage the community (T19b)
  const canManage = community && currentUser && isStaff(community.role);

  useEffect(() => {
    if (!slug) {
      navigate("/communities");
    }
  }, [slug, navigate]);

  // Links shared before T14 carry the name: move to the canonical URL with the slug
  useEffect(() => {
    if (community?.slug && community.slug !== slug) {
      navigate(communityPath(community), { replace: true });
    }
  }, [community, slug, navigate]);

  const handleToggleCommunity = (newRole) => {
    setCommunity((prev) => {
      const wasJoined = !!prev.role;
      const isJoining = !!newRole;
      const delta = isJoining && !wasJoined ? 1 : !isJoining && wasJoined ? -1 : 0;
      return { ...prev, role: newRole, memberCount: prev.memberCount + delta };
    });
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
        {canManage && (
          <button
            className="settings-icon-btn"
            onClick={() => navigate(communityPath(community, "/settings"))}
            aria-label="Ir a ajustes de comunidad"
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

      <div className="profile-main">
        <h2 className="username">{community.name}</h2>
        
        {community.description && (
          <p className="bio">{community.description}</p>
        )}

        {(community.visibility === "PRIVATE" || community.city || community.province) && (
          <p className="community-meta">
            {community.visibility === "PRIVATE" && "Comunidad privada"}
            {community.visibility === "PRIVATE" && (community.city || community.province) && " · "}
            {[community.city, community.province].filter(Boolean).join(", ")}
          </p>
        )}

        <div className="profile-stats">
          <div className="stat">
            <strong>{community.memberCount || 0}</strong>
            <span className="label">Miembros</span>
          </div>
        </div>

        <div className="profile-actions">
          {community.visibility === "PRIVATE" && !community.role ? (
            <CommunityRequestButton
              communityId={community.id}
              joinRequested={community.joinRequested}
              onRequested={() => setCommunity((prev) => ({ ...prev, joinRequested: true }))}
            />
          ) : (
            <CommunityJoinButton
              communityId={community.id}
              role={community.role}
              joinLabel="Unirse"
              memberLabel="Miembro"
              onToggle={handleToggleCommunity}
            />
          )}
        </div>
      </div>

      <div className="community-events-wrapper">
        <CommunityEvents community={community} />
      </div>

      {members.length > 0 && (
        <div className="members-section">
          <h3 className="members-title">Miembros ({community.memberCount || members.length})</h3>
          
          <div className="members-grid">
            {members.map((member) => {
              const memberAvatarUrl = formatters.avatar(member.profilePictureUrl, member.username, member.username);
              const hasError = memberAvatarErrors[member.username];
              
              return (
                <div key={member.username} className="member-card">
                  <div className="member-avatar">
                    {!hasError ? (
                      <img 
                        src={memberAvatarUrl} 
                        alt={member.username}
                        onError={() => setMemberAvatarErrors(prev => ({ ...prev, [member.username]: true }))}
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