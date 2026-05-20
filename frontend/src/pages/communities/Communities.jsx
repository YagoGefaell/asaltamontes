import "./Communities.css";
import { useState, useCallback, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useCommunities } from "../../features/communities/hooks/useCommunities.js";
import { createCommunityRequest, joinCommunityRequest, leaveCommunityRequest } from "../../features/communities/services/communities.service.js";
import { formatters } from "../../utils/helpers.js";
import Loading from "../../shared/components/Loading";
import Button from "../../shared/forms/Button";
import CommunityCreatorPanel from "./components/CommunityCreatorPanel";

const Communities = () => {
  const navigate = useNavigate();
  const { communities, loading, error, refetch } = useCommunities();
  const [isCreatorOpen, setIsCreatorOpen] = useState(false);
  const [creatingCommunity, setCreatingCommunity] = useState(false);
  const [imageErrors, setImageErrors] = useState({});
  const [joinLoadingMap, setJoinLoadingMap] = useState({});
  const [membershipMap, setMembershipMap] = useState({});

  // Initialize membership map from communities data
  useEffect(() => {
    if (communities.length > 0) {
      const map = {};
      communities.forEach(c => {
        map[c.id] = c.isMember;
      });
      setMembershipMap(map);
    }
  }, [communities]);

  const openCreator = useCallback(() => {
    setIsCreatorOpen(true);
  }, []);

  const closeCreator = useCallback(() => {
    if (creatingCommunity) return;
    setIsCreatorOpen(false);
  }, [creatingCommunity]);

  const handleCreateCommunity = useCallback(
    async (communityData) => {
      if (creatingCommunity) return;
      setCreatingCommunity(true);

      try {
        await createCommunityRequest(communityData);
        setIsCreatorOpen(false);
        await refetch();
      } finally {
        setCreatingCommunity(false);
      }
    },
    [creatingCommunity, refetch]
  );

  const handleJoinCommunity = useCallback(
    async (e, communityId) => {
      e.stopPropagation();
      
      setJoinLoadingMap(prev => ({ ...prev, [communityId]: true }));
      
      try {
        await joinCommunityRequest(communityId);
        setMembershipMap(prev => ({ ...prev, [communityId]: true }));
      } catch (err) {
        console.error("Error joining community:", err);
      }
    },
    []
  );

      } finally {
        setJoinLoadingMap(prev => ({ ...prev, [communityId]: false }));
      }
    },
    []
  );

  const handleLeaveCommunity = useCallback(
    async (e, communityId) => {
      e.stopPropagation();
      
      setJoinLoadingMap(prev => ({ ...prev, [communityId]: true }));
      
      try {
        await leaveCommunityRequest(communityId);
        setMembershipMap(prev => ({ ...prev, [communityId]: false }));
      } catch (err) {
        console.error("Error leaving community:", err);
      } finally {
        setJoinLoadingMap(prev => ({ ...prev, [communityId]: false }));
      }
    },
    []
  );

  if (isCreatorOpen) {
    return (
      <CommunityCreatorPanel
        isOpen={isCreatorOpen}
        creatingCommunity={creatingCommunity}
        onClose={closeCreator}
        onSubmitCommunity={handleCreateCommunity}
      />
    );
  }

  return (
    <div className="communities-page">
      <div className="communities-header">
        <h2>Comunidades</h2>
        <div className="communities-header-actions">
          <button
            type="button"
            className="create-community-btn"
            aria-label="Crear comunidad"
            title="Crear comunidad"
            onClick={openCreator}
            disabled={creatingCommunity}
          >
            <svg
              className="create-community-icon"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                d="M12 5V19M5 12H19"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>

      <div className="communities-grid">
        {loading && (
          <div className="communities-loading">
            <Loading />
          </div>
        )}

        {error && <p className="communities-error">{error}</p>}

        {!loading && !error && (
          <>
            {communities.length > 0 ? (
              communities.map((community) => {
                const isMember = membershipMap[community.id] ?? community.isMember;
                const isLoading = joinLoadingMap[community.id];
                
                return (
                  <div 
                    key={community.id} 
                    className="community-card"
                    onClick={() => navigate(`/communities/${community.name}`)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        navigate(`/communities/${community.name}`);
                      }
                    }}
                  >
                    <div className="community-card-avatar">
                      {!imageErrors[community.name] ? (
                        <img 
                          src={formatters.avatar(community.imageUrl, community.name, community.name)}
                          alt={community.name}
                          className="community-avatar-image"
                          onError={() => setImageErrors(prev => ({ ...prev, [community.name]: true }))}
                        />
                      ) : (
                        <div className="community-avatar-fallback" aria-hidden="true">
                          {formatters.getInitial(community.name, community.name)}
                        </div>
                      )}
                    </div>
                    <h3>{community.name}</h3>
                    <span className="community-members">Miembros</span>
                    
                    <div className="community-card-actions">
                      {!isMember ? (
                        <Button 
                          onClick={(e) => handleJoinCommunity(e, community.id)}
                          disabled={isLoading}
                          size="small"
                        >
                          {isLoading ? "Uniéndose..." : "Unirse"}
                        </Button>
                      ) : (
                        <Button 
                          onClick={(e) => handleLeaveCommunity(e, community.id)}
                          disabled={isLoading}
                          variant="secondary"
                          size="small"
                        >
                          {isLoading ? "Saliendo..." : "Salir"}
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })
            ) : (
              <p className="no-results">No hay comunidades disponibles</p>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default Communities;
