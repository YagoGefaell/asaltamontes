import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { getUserByUsername } from "../../features/users/services/users.service";
import Profile from "./Profile";

function UserProfile() {
  const { username } = useParams();
  const navigate = useNavigate();
  const [userProfile, setUserProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchUserProfile = async () => {
      if (!username) {
        navigate("/search");
        return;
      }

      setLoading(true);
      setError(null);
      
      try {
        const userData = await getUserByUsername(username);
        setUserProfile(userData);
      } catch (err) {
        console.error("Error fetching user profile:", err);
        setError(err.message || "Error al cargar el perfil");
      } finally {
        setLoading(false);
      }
    };

    fetchUserProfile();
  }, [username, navigate]);

  return (
    <Profile
      userData={userProfile}
      isOwnProfile={false}
      loading={loading}
      error={error}
      onBack={() => navigate(-1)}
    />
  );
}

export default UserProfile;