import "./CommunitySettingsMenu.css";
import { useParams, useNavigate } from "react-router-dom";
import { useCommunityDetail } from "../../features/communities/hooks/useCommunityDetail";
import { deleteCommunityRequest } from "../../features/communities/services/communities.service";

export default function CommunitySettingsMenu() {
  const { communityName } = useParams();
  const navigate = useNavigate();
  const { community } = useCommunityDetail(communityName);

  const handleDeleteCommunity = async () => {
    if (window.confirm("¿Estás segura de que quieres borrar la comunidad? Esta acción es irreversible.")) {
      try {
        if (community?.id) {
          await deleteCommunityRequest(community.id);
          navigate("/communities");
        }
      } catch (error) {
        console.error("Error al borrar la comunidad:", error);
        alert("Error al borrar la comunidad: " + error.message);
      }
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
        { label: "Borrar comunidad", action: handleDeleteCommunity, isAction: true, isDanger: true },
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
    </div>
  );
}
