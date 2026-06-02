import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import UserCard from "../../shared/ui/UserCard";
import Loading from "../../shared/components/Loading";
import "./FollowersFollowing.css";

function FollowListPage({ 
  type, 
  title, 
  fetchFunction, 
  emptyMessage, 
  searchPlaceholder,
  username = null
}) {
  const navigate = useNavigate();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  const fetchUsers = React.useCallback(async (page = 0, reset = false) => {
    if (page === 0) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    setError(null);

    try {
      let pageData;

      if (username) {
        pageData = await fetchFunction(username, page, 20);
      } else {
        pageData = await fetchFunction(null, page, 20);
      }

      let newUsers = pageData.content || [];

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
      setCurrentPage(page);
    } catch (err) {
      console.error(`Error fetching ${type}:`, err);
      setError(err.message || `Error al cargar ${type}`);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [fetchFunction, type]);

  useEffect(() => {
    fetchUsers(0, true);
  }, [fetchUsers]);

  const handleLoadMore = () => {
    if (!loadingMore && hasMore) {
      fetchUsers(currentPage + 1);
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
                    showFollowButton={user.isFollowing ? true : false}
                    isFollowing={user.isFollowing}
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