import "./CommunitySettingsMenu.css";
import { useParams, useNavigate } from "react-router-dom";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import { communityPath } from "../../features/communities/utils/communityPath";
import { deleteCommunityRequest, getCommunityChatRequest, setCommunityChatEnabledRequest } from "../../features/communities/services/communities.service";
import { useEffect, useState } from "react";
import ConfirmSheet from "../../shared/ui/ConfirmSheet";
import FormError from "../../shared/forms/FormError";
import { isStaff } from "../../features/communities/utils/communityErrors";
import Loading from "../../shared/components/Loading";
import { useAuth } from "../../features/auth/hooks/useAuth";

export default function CommunitySettingsMenu() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { community, loading } = useCommunityDetail(slug);
  const { currentUser } = useAuth();
  const [error, setError] = useState("");
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  // null until known: the chat of the community can be switched off by the moderation (T20)
  const [chatEnabled, setChatEnabled] = useState(null);

  useEffect(() => {
    if (!community?.id || community.role !== 'OWNER') return;
    let cancelled = false;
    getCommunityChatRequest(community.id)
      .then((chat) => { if (!cancelled) setChatEnabled(Boolean(chat.enabled)); })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [community?.id, community?.role]);

  const handleToggleChat = async () => {
    const next = !chatEnabled;
    setError("");
    try {
      await setCommunityChatEnabledRequest(community.id, next);
      setChatEnabled(next);
    } catch (err) {
      setError(err.message);
    }
  };

  const isOwner = community?.role === 'OWNER';

  // Owner and moderators manage the community (T19b); anyone else goes back to its page
  useEffect(() => {
    if (!loading && community && currentUser && !isStaff(community.role)) {
      navigate(communityPath(community));
    }
  }, [loading, community, currentUser, navigate]);

  const handleDeleteCommunity = async () => {
    setDeleting(true);
    setError("");
    try {
      if (community?.id) {
        await deleteCommunityRequest(community.id);
        navigate("/communities");
      }
    } catch (err) {
      console.error("Error al borrar la comunidad:", err);
      setError("No se ha podido borrar la comunidad: " + err.message);
    } finally {
      setDeleting(false);
    }
  };

  const go = (section) => () => navigate(communityPath(community, `/settings/${section}`));

  const management = [
    { label: "Miembros", action: go("members"), isAction: true },
    ...(community?.visibility === 'PRIVATE'
      ? [{ label: "Solicitudes de unión", action: go("requests"), isAction: true }]
      : []),
    { label: "Expulsadas", action: go("banned"), isAction: true },
    { label: "Registro de moderación", action: go("log"), isAction: true },
    ...(!isOwner || chatEnabled === null ? [] : [{
      label: chatEnabled ? "Desactivar el chat de la comunidad" : "Activar el chat de la comunidad",
      action: handleToggleChat,
      isAction: true,
    }]),
  ];

  const sections = [
    { title: "Información", items: [{ label: "Editar comunidad", action: go("edit"), isAction: true }] },
    { title: "Gestión", items: management },
    ...(isOwner
      ? [{
          title: "Peligro",
          items: [{ label: "Borrar comunidad", action: () => setConfirmingDelete(true), isAction: true, isDanger: true }],
        }]
      : []),
  ];

  const handleItemClick = (item) => item.action();

  if (loading || !community) return <Loading />;

  return (
    <div className="community-settings-menu">
      {sections.map((section) => (
        <div key={section.title} className="settings-section">
          <h3 className="section-title">{section.title}</h3>
          <ul className="section-list">
            {section.items.map((item) => (
              <li
                key={item.label}
                className={`settings-item ${item.isDanger ? 'danger-item' : ''}`}
                onClick={() => handleItemClick(item)}
                onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && handleItemClick(item)}
                role="button"
                tabIndex={0}
              >
                <span>{item.label}</span>
                <span className="arrow">›</span>
              </li>
            ))}
          </ul>
        </div>
      ))}
      <FormError message={confirmingDelete ? "" : error} />
      <ConfirmSheet
        open={confirmingDelete}
        title="Borrar comunidad"
        message="¿Seguro que quieres borrar la comunidad? No se puede deshacer."
        confirmLabel="Borrar"
        destructive
        loading={deleting}
        error={error}
        onConfirm={handleDeleteCommunity}
        onCancel={() => { if (!deleting) { setConfirmingDelete(false); setError(""); } }}
      />
    </div>
  );
}
