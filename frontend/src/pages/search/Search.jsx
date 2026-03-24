import { useCallback, useEffect, useRef, useState } from "react";
import UserCard from "../../shared/ui/UserCard";
import Loading from "../../shared/components/Loading";
import "./Search.css";
import { getAllUsersRequest } from "../../features/users/services/users.service";
import {
  followUserRequest,
  getFollowingRequest,
  unfollowUserRequest,
} from "../../features/users/services/users.service.js";
import { useAuth } from "../../features/auth/hooks/useAuth";

const Search = () => {
  const { currentUser, incrementFollowing, decrementFollowing } = useAuth();
  const [searchTerm, setSearchTerm] = useState("");
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [followLoadingMap, setFollowLoadingMap] = useState({});
  const [error, setError] = useState(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [searchTimeout, setSearchTimeout] = useState(null);
  const [followingUsernames, setFollowingUsernames] = useState([]);
  const initializedRef = useRef(false);

  const loadFollowingUsernames = async () => {
    try {
      const usernames = [];
      let page = 0;
      let last = false;

      while (!last) {
        const pageData = await getFollowingRequest(page, 200);
        const content = pageData?.content || [];

        content.forEach((user) => {
          if (user?.username) {
            usernames.push(user.username);
          }
        });

        last = pageData?.last ?? true;
        page += 1;

        if (content.length === 0) {
          last = true;
        }
      }

      setFollowingUsernames([...new Set(usernames)]);
    } catch (err) {
      console.warn("No se pudo cargar la lista de seguidos", err);
    }
  };

  const fetchUsers = useCallback(async (page = 0, query = "", reset = false) => {
    if (page === 0) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    setError(null);

    try {
      const pageData = await getAllUsersRequest(page, 20, query);
      const newUsers = (pageData.content || []).map((user) => ({
        ...user,
        isFollowing: followingUsernames.includes(user.username),
      }));

      if (reset || page === 0) {
        setUsers(newUsers);
      } else {
        setUsers((prev) => [...prev, ...newUsers]);
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
  }, [followingUsernames]);

  useEffect(() => {
    if (initializedRef.current) {
      return;
    }

    initializedRef.current = true;
    loadFollowingUsernames();
    fetchUsers(0, "", true);
  }, [fetchUsers]);

  useEffect(() => {
    setUsers((prev) =>
      prev.map((user) => ({
        ...user,
        isFollowing: followingUsernames.includes(user.username),
      }))
    );
  }, [followingUsernames]);

  const handleSearch = (e) => {
    const query = e.target.value;
    setSearchTerm(query);

    if (searchTimeout) {
      clearTimeout(searchTimeout);
    }

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

  const handleFollowToggle = async (username) => {
    setFollowLoadingMap((prev) => ({ ...prev, [username]: true }));

    try {
      const user = users.find((item) => item.username === username);
      const currentlyFollowing = Boolean(user?.isFollowing);

      if (currentlyFollowing) {
        await unfollowUserRequest(username);
        setFollowingUsernames((prev) => prev.filter((item) => item !== username));
        decrementFollowing();
      } else {
        await followUserRequest(username);
        setFollowingUsernames((prev) => [...new Set([...prev, username])]);
        incrementFollowing();
      }

      setUsers((prev) =>
        prev.map((item) =>
          item.username === username
            ? { ...item, isFollowing: !currentlyFollowing }
            : item
        )
      );
    } catch (err) {
      setError(err.message || "Error actualizando seguimiento");
    } finally {
      setFollowLoadingMap((prev) => ({ ...prev, [username]: false }));
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

        {error && <p className="search-error">{error}</p>}

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
                    showFollowButton={currentUser?.username !== user.username}
                    isFollowing={Boolean(user.isFollowing)}
                    onFollowToggle={handleFollowToggle}
                    followLoading={Boolean(followLoadingMap[user.username])}
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
