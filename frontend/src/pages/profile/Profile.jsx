import "./Profile.css";
import { useState, useEffect } from "react";
import { FaCog, FaPlus, FaEllipsisH, FaCheckCircle, FaMapMarkerAlt } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { useUserProfile } from "../../features/users/hooks/useUserProfile";
import FollowUserButton from "../../shared/components/FollowUserButton";
import Button from "../../shared/forms/Button";
import Loading from "../../shared/components/Loading";
import Avatar from "../../shared/ui/Avatar";
import PageHeader from "../../shared/ui/PageHeader";
import Tabs from "../../shared/ui/Tabs";
import BottomSheet from "../../shared/ui/BottomSheet";
import { ProfilePosts, ProfileEvents, ProfileCommunities } from "./ProfileTabs";
import FormError from "../../shared/forms/FormError";
import { blockUserRequest, unblockUserRequest } from "../../features/users/services/users.service";

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
  const [tab, setTab] = useState("posts");
  const [menuOpen, setMenuOpen] = useState(false);
  const [blockError, setBlockError] = useState("");
  // Local override after (un)blocking; null = what the server said
  const [blockedOverride, setBlockedOverride] = useState(null);

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
  
  // Reversible: no confirmation. A failure is shown inside the sheet, which stays open to retry
  const handleToggleBlock = async (isBlocked) => {
    const username = profileData?.username;
    if (!username) return;
    setBlockError("");
    try {
      if (isBlocked) {
        await unblockUserRequest(username);
      } else {
        await blockUserRequest(username);
      }
      setBlockedOverride(!isBlocked);
      setMenuOpen(false);
    } catch (err) {
      setBlockError(err.message);
    }
  };

  if (isLoading) return <Loading />;

  if (error) {
    return (
      <div className="profile-page">
        <PageHeader title="Perfil" onBack={onBack} />
        <div className="pf-error">
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
    fullName = "",
    bio = "",
    city = "",
    profilePictureUrl,
    runningLevel = "",
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
    INTERMEDIATE: "Intermedia",
    ADVANCED: "Avanzada",
    EXPERT: "Experta",
  };

  const isBlockedByMe = blockedOverride ?? Boolean(profileData.isBlockedByMe);
  const base = isOwnProfile ? "/me" : `/users/${username}`;
  const tabs = [
    { id: "posts", label: "Publicaciones" },
    { id: "events", label: "Quedadas" },
    { id: "communities", label: "Comunidades" },
  ];

  return (
    <div className="profile-page">
      <PageHeader
        title={`@${username}`}
        onBack={isOwnProfile ? undefined : onBack}
        actions={
          isOwnProfile ? (
            <>
              <button type="button" onClick={() => navigate("/posts/new")} aria-label="Nueva publicación">
                <FaPlus aria-hidden="true" />
              </button>
              <button type="button" onClick={() => navigate("/settings")} aria-label="Ir a ajustes">
                <FaCog aria-hidden="true" />
              </button>
            </>
          ) : (
            <button type="button" onClick={() => setMenuOpen(true)} aria-label="Más opciones" aria-haspopup="dialog">
              <FaEllipsisH aria-hidden="true" />
            </button>
          )
        }
      />

      <section className="pf-top" aria-label="Datos del perfil">
        <Avatar src={profilePictureUrl} name={fullName || username} size="xl" />
        <div className="pf-stats">
          <button type="button" className="pf-stat" onClick={() => navigate(`${base}/followers`)} aria-label="Ver seguidoras">
            <span className="pf-stat__count">{displayFollowersCount}</span>
            <span className="pf-stat__label">Seguidoras</span>
          </button>
          <button type="button" className="pf-stat" onClick={() => navigate(`${base}/following`)} aria-label="Ver a quién sigue">
            <span className="pf-stat__count">{displayFollowingCount}</span>
            <span className="pf-stat__label">Siguiendo</span>
          </button>
        </div>
      </section>

      <div className="pf-info">
        <p className="pf-name">
          {fullName || username}
          {isVerified && <FaCheckCircle className="pf-verified" aria-label="Cuenta verificada" />}
        </p>
        {bio && <p className="pf-bio">{bio}</p>}
        <p className="pf-meta">
          {levelLabels[runningLevel] && (
            <span className={`pf-level pf-level--${runningLevel.toLowerCase()}`}>{levelLabels[runningLevel]}</span>
          )}
          {city && (
            <span className="pf-city">
              <FaMapMarkerAlt aria-hidden="true" /> {city}
            </span>
          )}
        </p>
      </div>

      {!isOwnProfile && isBlockedByMe && (
        <p className="pf-blocked">Has bloqueado a esta usuaria.</p>
      )}

      <div className="pf-actions">
        {isOwnProfile ? (
          <Button variant="secondary" onClick={() => navigate("/settings/profile")}>Editar perfil</Button>
        ) : (
          !isBlockedByMe && (
            <>
              <FollowUserButton
                userId={profileData.username}
                initialIsFollowing={profileData.isFollowing || false}
              />
              <Button
                variant="secondary"
                onClick={handleOpenChat}
                loading={openChatLoading}
                disabled={openChatLoading}
              >
                Mensaje
              </Button>
            </>
          )
        )}
      </div>

      {(isOwnProfile || !isBlockedByMe) && (
        <>
          <Tabs tabs={tabs} value={tab} onChange={setTab} label="Contenido del perfil" idPrefix="profile" className="pf-tabs" />
          {tab === "posts" && <ProfilePosts username={username} isOwn={isOwnProfile} />}
          {tab === "events" && <ProfileEvents username={isOwnProfile ? null : username} />}
          {tab === "communities" && <ProfileCommunities username={isOwnProfile ? null : username} />}
        </>
      )}

      <BottomSheet open={menuOpen} title={`@${username}`} onClose={() => { setMenuOpen(false); setBlockError(""); }}>
        <FormError message={blockError} />
        <button type="button" className="pf-sheet-action pf-sheet-action--danger" onClick={() => handleToggleBlock(isBlockedByMe)}>
          {isBlockedByMe ? "Desbloquear" : "Bloquear"}
        </button>
      </BottomSheet>
    </div>
  );
}

export default Profile;
