import { useParams } from "react-router-dom";
import SettingsLayout from "./SettingsLayout.jsx";

function CommunitySettingsPage({ children }) {
  const { communityName } = useParams();
  
  return (
    <SettingsLayout 
      title="Ajustes de Comunidad" 
      backTo={`/communities/${communityName}`}
    >
      {children}
    </SettingsLayout>
  );
}

export default CommunitySettingsPage;
