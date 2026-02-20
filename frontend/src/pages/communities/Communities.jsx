import "./Communities.css";
import { useState } from "react";

const Communities = () => {
  const [communities] = useState([
    { id: 1, name: "Mujeres Tech", members: "1.2K", icon: "👩‍💻" },
    { id: 2, name: "Emprendedoras", members: "856", icon: "🚀" },
    { id: 3, name: "Salud Femenina", members: "2.3K", icon: "💚" },
    { id: 4, name: "Artes Creativas", members: "567", icon: "🎨" },
  ]);

  return (
    <div className="communities-page">
      <div className="communities-header">
        <h2>Comunidades</h2>
      </div>

      <div className="communities-grid">
        {communities.map((community) => (
          <div key={community.id} className="community-card">
            <div className="community-icon">{community.icon}</div>
            <h3>{community.name}</h3>
            <span className="community-members">{community.members} miembros</span>
            <button className="btn-join">Unirse</button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Communities;
