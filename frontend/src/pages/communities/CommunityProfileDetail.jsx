import "../profile/Profile.css";
import "./CommunityProfileDetail.css";
import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { FaCog, FaMapMarkerAlt } from "react-icons/fa";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import Loading from "../../shared/components/Loading";
import FormError from "../../shared/forms/FormError";
import PageHeader from "../../shared/ui/PageHeader";
import Avatar from "../../shared/ui/Avatar";
import Tabs from "../../shared/ui/Tabs";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import { getCommunityEventsRequest } from "../../features/events/services/events.service";
import { CommunityPostsTab, CommunityEventsTab, CommunityMembersTab } from "./CommunityProfileTabs";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import { communityPath } from "../../features/communities/utils/communityPath";
import { isStaff } from "../../features/communities/utils/communityErrors";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import CommunityChatButton from "../../features/communities/components/CommunityChatButton";
import ReportMenu from "../../features/reports/components/ReportMenu";
import { useAuth } from "../../features/auth/hooks/useAuth";

function CommunityProfile() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const { community, setCommunity, loading, error } = useCommunityDetail(slug);
  const [tab, setTab] = useState("posts");
  // Shared by the "Quedadas" counter and the tab, so it is requested once
  const eventsSection = useSearchSection(
    (page) => getCommunityEventsRequest(community.slug || community.name, { page, size: 10 }),
    `community-events:${community?.id}`,
    Boolean(community),
  );

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
        <PageHeader title="Comunidad" onBack={() => navigate(-1)} />
        <FormError message={error || "Comunidad no encontrada"} />
      </div>
    );
  }

  const tabs = [
    { id: "posts", label: "Publicaciones" },
    { id: "events", label: "Quedadas" },
    { id: "members", label: "Miembros" },
  ];
  const location = [community.city, community.province].filter(Boolean).join(", ");
  const eventsCount = eventsSection.loading || eventsSection.error
    ? "–"
    : `${eventsSection.items.length}${eventsSection.hasMore ? "+" : ""}`;

  return (
    <div className="profile-page community-profile-page">
      <PageHeader
        title={community.name}
        onBack={() => navigate(-1)}
        actions={(
          <>
            {canManage && (
              <button type="button" onClick={() => navigate(communityPath(community, "/settings"))} aria-label="Ir a ajustes de comunidad" title="Ajustes de la comunidad">
                <FaCog aria-hidden="true" />
              </button>
            )}
            {community.role !== "OWNER" && (
              <ReportMenu
                menuTitle={community.name}
                reportTitle="Denunciar comunidad"
                actionLabel="Denunciar comunidad"
                targetType="COMMUNITY"
                target={community.slug || community.name}
              />
            )}
          </>
        )}
      />

      <section className="pf-top" aria-label="Datos de la comunidad">
        <Avatar src={community.imageUrl} name={community.name} size="xl" />
        <div className="pf-stats">
          <button type="button" className="pf-stat" onClick={() => setTab("members")}>
            <span className="pf-stat__count">{community.memberCount || 0}</span>
            <span className="pf-stat__label">Miembros</span>
          </button>
          <button type="button" className="pf-stat" onClick={() => setTab("events")}>
            <span className="pf-stat__count">{eventsCount}</span>
            <span className="pf-stat__label">Quedadas</span>
          </button>
        </div>
      </section>

      <div className="pf-info">
        <p className="pf-name">{community.name}</p>
        {community.description && <p className="pf-bio">{community.description}</p>}
        {(community.visibility === "PRIVATE" || location) && (
          <p className="pf-meta">
            {community.visibility === "PRIVATE" && <span>Comunidad privada</span>}
            {location && (
              <span className="pf-city">
                <FaMapMarkerAlt aria-hidden="true" /> {location}
              </span>
            )}
          </p>
        )}
      </div>

      <div className="pf-actions">
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
        {community.role && <CommunityChatButton community={community} />}
      </div>

      <Tabs tabs={tabs} value={tab} onChange={setTab} label="Contenido de la comunidad" idPrefix="community" className="pf-tabs" />
      {tab === "posts" && <CommunityPostsTab community={community} />}
      {tab === "events" && <CommunityEventsTab community={community} section={eventsSection} />}
      {tab === "members" && <CommunityMembersTab community={community} />}
    </div>
  );
}

export default CommunityProfile;
