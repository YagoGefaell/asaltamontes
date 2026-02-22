import { useEffect, useState } from "react";
import UserCard from "../../shared/ui/UserCard";
import Loading from "../../shared/components/Loading";
import "./Search.css";
import { getAllUsersRequest } from "../../features/users/services/users.service";

const Search = () => {
  const [searchTerm, setSearchTerm] = useState("");
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [searchTimeout, setSearchTimeout] = useState(null);

  const fetchUsers = async (page = 0, query = "", reset = false) => {
    if (page === 0) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    setError(null);
    
    try {
      const pageData = await getAllUsersRequest(page, 20, query);
      const newUsers = pageData.content || [];
      
      if (reset || page === 0) {
        setUsers(newUsers);
      } else {
        setUsers(prev => [...prev, ...newUsers]);
      }
      
      setHasMore(!pageData.last);
      setCurrentPage(page);
    } catch (err) {
      console.error("Error fetching users:", err);
      setError(err.message || "Error al cargar usuarios");
      if (reset || page === 0) {
        setUsers([]);
      }
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  };

  // Load initial users when component mounts
  useEffect(() => {
    fetchUsers(0, "", true);
  }, []);

  const handleSearch = (e) => {
    const query = e.target.value;
    setSearchTerm(query);
    
    // Clear previous timeout
    if (searchTimeout) {
      clearTimeout(searchTimeout);
    }
    
    // Debounce search
    const timeout = setTimeout(() => {
      fetchUsers(0, query, true);
    }, 300);
    
    setSearchTimeout(timeout);
  };

  const handleLoadMore = () => {
    if (!loadingMore && hasMore) {
      fetchUsers(currentPage + 1, searchTerm);
    }
  };

  return (
    <div className="search-page">
      <div className="search-input-container">
        <input
          type="text"
          placeholder="Buscar usuarios..."
          value={searchTerm}
          onChange={handleSearch}
        />
      </div>

      <div className="search-results">
        {loading && (
          <div className="search-loading">
            <Loading />
          </div>
        )}
        
        {error && (
          <p className="search-error">{error}</p>
        )}
        
        {!loading && !error && (
          <>
            {users.length > 0 ? (
              <>
                {users.map((user, index) => (
                  <UserCard 
                    key={user.username || index} 
                    fullName={user.fullName}
                    username={user.username}
                    profilePictureUrl={user.profilePictureUrl}
                  />
                ))}
                
                {hasMore && (
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
                {searchTerm ? "No se encontraron usuarios" : "No hay usuarios disponibles"}
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
};

export default Search;
