import "./CommunitySettingsMenu.css";
import { useParams, useNavigate } from "react-router-dom";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import { communityPath } from "../../features/communities/utils/communityPath";
import { deleteCommunityRequest } from "../../features/communities/services/communities.service";
import { useEffect, useState } from "react";
import Modal from "../../shared/ui/Modal";
import Button from "../../shared/forms/Button";
import { useToast } from "../../shared/ui/Toast";
import { isStaff } from "../../features/communities/utils/communityErrors";
import Loading from "../../shared/components/Loading";
import { useAuth } from "../../features/auth/hooks/useAuth";

export default function CommunitySettingsMenu() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { community, loading } = useCommunityDetail(slug);
  const { currentUser } = useAuth();
  const toast = useToast();
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const isOwner = community?.role === 'OWNER';

  // Owner and moderators manage the community (T19b); anyone else goes back to its page
  useEffect(() => {
    if (!loading && community && currentUser && !isStaff(community.role)) {
      navigate(communityPath(community));
    }
  }, [loading, community, currentUser, navigate]);

  const handleDeleteCommunity = async () => {
    setDeleting(true);
    try {
      if (community?.id) {
        await deleteCommunityRequest(community.id);
        navigate("/communities");
      }
    } catch (error) {
      console.error("Error al borrar la comunidad:", error);
      toast.error("Error al borrar la comunidad: " + error.message);
    } finally {
      setDeleting(false);
      setConfirmingDelete(false);
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
      <Modal
        open={confirmingDelete}
        title="Borrar comunidad"
        onClose={() => !deleting && setConfirmingDelete(false)}
        footer={
          <>
            <Button variant="outline" size="small" onClick={() => setConfirmingDelete(false)} disabled={deleting}>
              Cancelar
            </Button>
            <Button variant="danger" size="small" onClick={handleDeleteCommunity} loading={deleting}>
              Borrar
            </Button>
          </>
        }
      >
        ¿Estás segura de que quieres borrar la comunidad? Esta acción es irreversible.
      </Modal>
    </div>
  );
}
