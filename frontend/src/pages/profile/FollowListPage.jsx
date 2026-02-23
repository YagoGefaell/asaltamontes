import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import UserCard from "../../shared/ui/UserCard";
import Loading from "../../shared/components/Loading";
import { followUserRequest, unfollowUserRequest } from "../../features/users/services/users.service";
import "./FollowersFollowing.css";

function FollowListPage({ 
  type, 
  title, 
  fetchFunction, 
  emptyMessage, 
  searchPlaceholder,
  resultsCountMessage 
}) {
  const navigate = useNavigate();
  const { incrementFollowing, decrementFollowing } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [followLoadingMap, setFollowLoadingMap] = useState({});
  const [followErrorMsg, setFollowErrorMsg] = useState(null);

  const fetchUsers = async (page = 0, reset = false) => {
    if (page === 0) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    setError(null);
    
    try {
      const pageData = await fetchFunction(page, 20);
      let newUsers = pageData.content || [];
      
      // Backend should provide isFollowing, but ensure it's set for "following" list as fallback
      if (type === 'following') {
        newUsers = newUsers.map(user => ({
          ...user,
          isFollowing: user.isFollowing !== undefined ? user.isFollowing : true
        }));
      }
      
      if (reset || page === 0) {
        setUsers(newUsers);
      } else {
        setUsers(prev => [...prev, ...newUsers]);
      }
      
      setHasMore(!pageData.last);
      setTotalElements(pageData.totalElements || 0);
      setCurrentPage(page);
    } catch (err) {
      console.error(`Error fetching ${type}:`, err);
      setError(err.message || `Error al cargar ${type}`);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  };

  useEffect(() => {
    fetchUsers(0, true);
  }, []);

  const handleLoadMore = () => {
    if (!loadingMore && hasMore) {
      fetchUsers(currentPage + 1);
    }
  };

  const handleFollowToggle = async (username) => {
    const user = users.find(u => u.username === username);
    if (!user) return;

    setFollowLoadingMap(prev => ({ ...prev, [username]: true }));
    setFollowErrorMsg(null);

    try {
      if (user.isFollowing) {
        await unfollowUserRequest(username);
        // Update global following count
        decrementFollowing();
        // Remove from list if in "following" page
        if (type === 'following') {
          setUsers(prev => prev.filter(u => u.username !== username));
          setTotalElements(prev => Math.max(0, prev - 1));
        } else {
          // Update isFollowing status for followers page
          setUsers(prev => prev.map(u => 
            u.username === username ? { ...u, isFollowing: false } : u
          ));
        }
      } else {
        await followUserRequest(username);
        // Update global following count
        incrementFollowing();
        setUsers(prev => prev.map(u => 
          u.username === username ? { ...u, isFollowing: true } : u
        ));
      }
    } catch (err) {
      console.error('Error toggling follow:', err);
      const errorMessage = err.message || 'Error al realizar la acción';
      setFollowErrorMsg(errorMessage);
      // Hide error after 3 seconds
      setTimeout(() => setFollowErrorMsg(null), 3000);
    } finally {
      setFollowLoadingMap(prev => ({ ...prev, [username]: false }));
    }
  };

  const filteredUsers = users.filter(user => 
    user.username?.toLowerCase().includes(searchTerm.toLowerCase()) ||
    user.fullName?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className={`${type}-page`}>
      <div className={`${type}-header`}>
        <button
          className="back-btn"
          onClick={() => navigate(-1)}
          aria-label="Volver atrás"
        >
          <svg
            className="back-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <h2 className={`${type}-title`}>{title}</h2>
      </div>

      <div className="search-input-container">
        <input
          type="text"
          placeholder={searchPlaceholder}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
      </div>

      {followErrorMsg && (
        <div className="follow-error-banner">
          {followErrorMsg}
        </div>
      )}

      <div className={`${type}-results`}>
        {loading && (
          <div className={`${type}-loading`}>
            <Loading />
          </div>
        )}
        
        {error && (
          <p className={`${type}-error`}>{error}</p>
        )}
        
        {!loading && !error && (
          <>
            {filteredUsers.length > 0 ? (
              <>
                {filteredUsers.map((user, index) => (
                  <UserCard 
                    key={user.username || index} 
                    fullName={user.fullName}
                    username={user.username}
                    profilePictureUrl={user.profilePictureUrl}
                    showFollowButton={true}
                    isFollowing={user.isFollowing ?? (type === 'following')}
                    onFollowToggle={handleFollowToggle}
                    followLoading={followLoadingMap[user.username] || false}
                  />
                ))}
                
                {!searchTerm && hasMore && (
                  <div className="load-more-container">
                    <button 
                      className="load-more-btn" 
                      onClick={handleLoadMore}
                      disabled={loadingMore}
                    >
                      {loadingMore ? "Cargando..." : "Cargar más"}
                    </button>
                  </div>
                )}
              </>
            ) : (
              <p className="no-results">
                {searchTerm ? 
                  `No se encontraron usuarios que coincidan con "${searchTerm}"` :
                  emptyMessage
                }
              </p>
            )}
          </>
        )}
        
        {loadingMore && (
          <div className="loading-more">
            <Loading />
          </div>
        )}
      </div>
    </div>
  );
}

export default FollowListPage;