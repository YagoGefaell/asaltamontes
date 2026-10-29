import { useEffect } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import Loading from "../../../shared/components/Loading";
import FormError from "../../../shared/forms/FormError";
import { useCommunityDetail } from "../../../features/communities/hooks/useCommunityDetail";
import { communityPath } from "../../../features/communities/utils/communityPath";
import { isStaff } from "../../../features/communities/utils/communityErrors";
import CommunityEditForm from "./CommunityEditForm";
import MemberAdminList from "./MemberAdminList";
import ModerationLog from "./ModerationLog";
import { BannedList, JoinRequestList } from "./PersonRequestList";
import "./CommunityModeration.css";

const SECTIONS = {
  edit: { title: "Datos de la comunidad" },
  members: { title: "Miembros" },
  requests: { title: "Solicitudes de unión", privateOnly: true },
  banned: { title: "Expulsadas" },
  log: { title: "Registro de moderación" },
};

/** One screen of the moderation area: /communities/:slug/settings/:section (owner and moderators). */
export default function CommunitySettingsSection() {
  const { slug, section } = useParams();
  const navigate = useNavigate();
  const { community, setCommunity, loading, error, refetch } = useCommunityDetail(slug);
  const definition = SECTIONS[section];

  const allowed = community && isStaff(community.role);
  useEffect(() => {
    if (!loading && community && !allowed) navigate(communityPath(community), { replace: true });
  }, [loading, community, allowed, navigate]);

  if (loading) return <Loading />;
  if (error || !community) return <FormError message={error || "Comunidad no encontrada"} />;
  if (!allowed) return null;

  const settingsPath = communityPath(community, "/settings");
  if (!definition || (definition.privateOnly && community.visibility !== "PRIVATE")) {
    return <FormError message="Esta sección no existe." />;
  }

  return (
    <div className="community-settings-section">
      <Link to={settingsPath} className="moderation-back">‹ Ajustes de la comunidad</Link>
      <h2 className="moderation-section-title">{definition.title}</h2>
      {section === "edit" && (
        <CommunityEditForm
          community={community}
          onSaved={(updated) => {
            setCommunity((prev) => ({ ...prev, ...updated }));
            navigate(settingsPath);
          }}
        />
      )}
      {section === "members" && <MemberAdminList community={community} onOwnershipTransferred={refetch} />}
      {section === "requests" && <JoinRequestList community={community} />}
      {section === "banned" && <BannedList community={community} />}
      {section === "log" && <ModerationLog community={community} />}
    </div>
  );
}
