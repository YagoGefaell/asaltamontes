import { useParams } from "react-router-dom";
import "./Profile.css";

const Profile = () => {
  const { username } = useParams();

  return (
    <div className="user-profile-page">
      <div className="profile-header">
        <img
          className="profile-avatar"
          src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${username}`}
          alt={username}
        />

        <h1 className="profile-username">@{username}</h1>
        <p className="profile-bio">
          Bio de ejemplo. Aquí irá la descripción pública de la usuaria.
        </p>
      </div>

      <div className="profile-stats">
        <div className="stat">
          <span className="stat-number">124</span>
          <span className="stat-label">Seguidores</span>
        </div>
        <div className="stat">
          <span className="stat-number">87</span>
          <span className="stat-label">Siguiendo</span>
        </div>
      </div>

      <div className="profile-actions">
        <button className="primary-button">Seguir</button>
        <button className="secondary-button">Mensaje</button>
      </div>
    </div>
  );
};

export default Profile;
