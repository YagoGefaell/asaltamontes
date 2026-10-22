import "./CommunitySettingsMenu.css";
import { useParams, useNavigate } from "react-router-dom";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import { communityPath } from "../../features/communities/utils/communityPath";
import { deleteCommunityRequest } from "../../features/communities/services/communities.service";
import { useEffect, useState } from "react";
import Modal from "../../shared/ui/Modal";
import Button from "../../shared/forms/Button";
import { useToast } from "../../shared/ui/Toast";
import { useAuth } from "../../features/auth/hooks/useAuth";

export default function CommunitySettingsMenu() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { community, loading } = useCommunityDetail(slug);
  const { currentUser } = useAuth();
  const toast = useToast();
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (!loading && community && currentUser) {
      const isOwner = community.role === 'OWNER';
      if (!isOwner) {
        navigate(communityPath(community));
      }
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

  const sections = [
    {
      title: "Información",
      items: [
        { label: "General", path: "#general", isPlaceholder: true },
        { label: "Descripción", path: "#description", isPlaceholder: true },
      ],
    },
    {
      title: "Gestión",
      items: [
        { label: "Miembros", path: "#members", isPlaceholder: true },
        { label: "Permisos", path: "#permissions", isPlaceholder: true },
      ],
    },
    {
      title: "Estadísticas",
      items: [
        { label: "Actividad", path: "#activity", isPlaceholder: true },
      ],
    },
    {
      title: "Peligro",
      items: [
        { label: "Borrar comunidad", action: () => setConfirmingDelete(true), isAction: true, isDanger: true },
      ],
    },
  ];

  const handleItemClick = (item) => {
    if (item.isPlaceholder) {
      // Placeholder para futuras funcionalidades
      console.log("Próximamente:", item.label);
    } else if (item.isAction) {
      item.action();
    }
  };

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
