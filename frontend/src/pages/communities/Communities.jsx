import "./Communities.css";
import { useState, useCallback, useEffect } from "react";
import { Link } from "react-router-dom";
import { FaSearch } from "react-icons/fa";
import { useCommunities } from "../../features/communities/hooks/useCommunities.js";
import { createCommunityRequest } from "../../features/communities/services/communities.service.js";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import Button from "../../shared/forms/Button";
import EmptyState from "../../shared/ui/EmptyState";
import Avatar from "../../shared/ui/Avatar";
import Skeleton from "../../shared/ui/Skeleton";
import { useToast } from "../../shared/ui/Toast";
import CommunityCreatorPanel from "./components/CommunityCreatorPanel";
import { communityPath } from "../../features/communities/utils/communityPath.js";

const SEARCH_DEBOUNCE_MS = 300;
const SKELETON_CARDS = 6;

const Communities = () => {
  const toast = useToast();
  const [cityText, setCityText] = useState("");
  const [provinceText, setProvinceText] = useState("");
  const [place, setPlace] = useState({ city: "", province: "" });
  const { communities, setCommunities, loading, loadingMore, hasMore, loadMore, error, refetch } = useCommunities("", place);
  const [isCreatorOpen, setIsCreatorOpen] = useState(false);
  const [creatingCommunity, setCreatingCommunity] = useState(false);

  // Search when the user stops typing
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setPlace({ city: cityText.trim(), province: provinceText.trim() });
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timeoutId);
  }, [cityText, provinceText]);

  const filtered = Boolean(place.city || place.province);
  const clearFilters = () => {
    setCityText("");
    setProvinceText("");
  };

  const openCreator = useCallback(() => {
    setIsCreatorOpen(true);
  }, []);

  const closeCreator = useCallback(() => {
    if (creatingCommunity) return;
    setIsCreatorOpen(false);
  }, [creatingCommunity]);

  const handleToggleCommunity = useCallback((communityId, newRole) => {
    setCommunities((prev) =>
      prev.map((c) => {
        if (c.id !== communityId) return c;
        const wasJoined = !!c.role;
        const isJoining = !!newRole;
        const delta = isJoining && !wasJoined ? 1 : !isJoining && wasJoined ? -1 : 0;
        return { ...c, role: newRole, memberCount: c.memberCount + delta };
      })
    );
  }, [setCommunities]);

  const handleToggleCommunityForButton = useCallback((communityId) => {
    return (newRole) => handleToggleCommunity(communityId, newRole);
  }, [handleToggleCommunity]);

  const handleCreateCommunity = useCallback(
    async (communityData) => {
      if (creatingCommunity) return;
      setCreatingCommunity(true);

      try {
        await createCommunityRequest(communityData);
        setIsCreatorOpen(false);
        toast.success("Comunidad creada");
        await refetch();
      } finally {
        setCreatingCommunity(false);
      }
    },
    [creatingCommunity, refetch, toast]
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
          <Link to="/search?tab=communities" className="communities-search-link" aria-label="Buscar comunidades">
            <FaSearch aria-hidden="true" />
          </Link>
          <Button
            variant="secondary"
            className="create-community-btn"
            aria-label="Crear comunidad"
            title="Crear comunidad"
            onClick={openCreator}
            disabled={creatingCommunity}
          >
            <svg className="create-community-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 5V19M5 12H19" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </Button>
        </div>
      </div>

      <div className="communities-search">
        <input
          type="search"
          className="communities-search-input"
          placeholder="Ciudad"
          aria-label="Filtrar por ciudad"
          value={cityText}
          onChange={(e) => setCityText(e.target.value)}
        />
        <input
          type="search"
          className="communities-search-input"
          placeholder="Provincia"
          aria-label="Filtrar por provincia"
          value={provinceText}
          onChange={(e) => setProvinceText(e.target.value)}
        />
      </div>

      <div className="communities-grid" aria-busy={loading || undefined}>
        {loading &&
          Array.from({ length: SKELETON_CARDS }, (_, i) => (
            <div key={i} className="community-card community-card--skeleton" data-testid="community-skeleton">
              <Skeleton width="80px" height="80px" circle />
              <Skeleton width="70%" height="1rem" />
              <Skeleton width="40%" height="0.75rem" />
            </div>
          ))}

        {!loading && error && (
          <div className="communities-error" role="alert">
            <p>{error}</p>
            <Button variant="secondary" onClick={refetch}>Reintentar</Button>
          </div>
        )}

        {!loading && !error && communities.length > 0 &&
          communities.map((community) => (
            <div key={community.id} className="community-card">
              <Link to={communityPath(community)} className="community-card-link">
                <Avatar src={community.imageUrl} name={community.name} size="xl" />
                <h3>{community.name}</h3>
                <span className="community-members">
                  {community.memberCount} {community.memberCount === 1 ? "miembro" : "miembros"}
                </span>
              </Link>
              <div className="community-card-actions">
                {community.visibility === "PRIVATE" && !community.role ? (
                  <CommunityRequestButton communityId={community.id} />
                ) : (
                  <CommunityJoinButton
                    communityId={community.id}
                    role={community.role}
                    joinLabel="Unirse"
                    memberLabel="Miembro"
                    onToggle={handleToggleCommunityForButton(community.id)}
                  />
                )}
              </div>
            </div>
          ))}

        {!loading && !error && communities.length === 0 && (
          <EmptyState
            className="communities-empty"
            title={filtered ? "No hay comunidades que coincidan con tu búsqueda" : "Todavía no hay comunidades"}
            description={filtered ? "Prueba con otro nombre o lugar." : "Crea la primera y reúne a tu grupo."}
            action={
              filtered ? (
                <Button variant="secondary" onClick={clearFilters}>Limpiar búsqueda</Button>
              ) : (
                <Button onClick={openCreator}>Crear comunidad</Button>
              )
            }
          />
        )}
      </div>

      {!loading && !error && hasMore && (
        <Button variant="secondary" className="communities-load-more" onClick={loadMore} loading={loadingMore}>
          Cargar más comunidades
        </Button>
      )}
    </div>
  );
};

export default Communities;
