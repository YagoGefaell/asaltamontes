import { useParams } from "react-router-dom";
import SettingsLayout from "./SettingsLayout.jsx";

function CommunitySettingsPage({ children }) {
  const { slug } = useParams();
  
  return (
    <SettingsLayout 
      title="Ajustes de Comunidad" 
      backTo={`/communities/${encodeURIComponent(slug)}`}
    >
      {children}
    </SettingsLayout>
  );
}

export default CommunitySettingsPage;
