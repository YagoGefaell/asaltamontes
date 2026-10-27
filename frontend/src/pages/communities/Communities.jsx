import "./Communities.css";
import { useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { FaPlus, FaSearch, FaSlidersH } from "react-icons/fa";
import { useCommunities } from "../../features/communities/hooks/useCommunities.js";
import { createCommunityRequest } from "../../features/communities/services/communities.service.js";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import Button from "../../shared/forms/Button";
import PageHeader from "../../shared/ui/PageHeader";
import BottomSheet from "../../shared/ui/BottomSheet";
import { Chip, ChipRow } from "../../shared/ui/Chip";
import Input from "../../shared/forms/Input";
import EmptyState from "../../shared/ui/EmptyState";
import Avatar from "../../shared/ui/Avatar";
import Skeleton from "../../shared/ui/Skeleton";
import { useToast } from "../../shared/ui/Toast";
import CommunityCreatorPanel from "./components/CommunityCreatorPanel";
import { communityPath } from "../../features/communities/utils/communityPath.js";

const SKELETON_CARDS = 6;

const Communities = () => {
  const toast = useToast();
  const [place, setPlace] = useState({ city: "", province: "" });
  const [sheetOpen, setSheetOpen] = useState(false);
  const [draft, setDraft] = useState(place);
  const { communities, setCommunities, loading, loadingMore, hasMore, loadMore, error, refetch } = useCommunities("", place);
  const [isCreatorOpen, setIsCreatorOpen] = useState(false);
  const [creatingCommunity, setCreatingCommunity] = useState(false);

  const count = [place.city, place.province].filter(Boolean).length;
  const filtered = count > 0;
  const clearFilters = () => {
    setPlace({ city: "", province: "" });
    setSheetOpen(false);
  };
  const openSheet = () => {
    setDraft(place);
    setSheetOpen(true);
  };
  const applyFilters = () => {
    setPlace({ city: draft.city.trim(), province: draft.province.trim() });
    setSheetOpen(false);
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
      <PageHeader
        title="Comunidades"
        actions={
          <>
            <Link to="/search?tab=communities" aria-label="Buscar comunidades"><FaSearch aria-hidden="true" /></Link>
            <button type="button" aria-label="Crear comunidad" title="Crear comunidad" onClick={openCreator} disabled={creatingCommunity}>
              <FaPlus aria-hidden="true" />
            </button>
          </>
        }
      />

      <ChipRow label="Filtros de comunidades">
        <Chip selected={filtered} onClick={openSheet} aria-haspopup="dialog">
          <FaSlidersH aria-hidden="true" /> {filtered ? `Filtros · ${count}` : "Filtros"}
        </Chip>
      </ChipRow>

      <BottomSheet
        open={sheetOpen}
        title="Filtros"
        onClose={() => setSheetOpen(false)}
        footer={
          <div className="communities-sheet__actions">
            <Button variant="ghost" onClick={clearFilters}>Quitar filtros</Button>
            <Button onClick={applyFilters}>Aplicar</Button>
          </div>
        }
      >
        <div className="communities-sheet__fields">
          <Input id="cf-city" label="Ciudad" value={draft.city} autoComplete="off" onChange={(e) => setDraft({ ...draft, city: e.target.value })} />
          <Input id="cf-province" label="Provincia" value={draft.province} autoComplete="off" onChange={(e) => setDraft({ ...draft, province: e.target.value })} />
        </div>
      </BottomSheet>

      <div className="communities-list" aria-busy={loading || undefined}>
        {loading &&
          Array.from({ length: SKELETON_CARDS }, (_, i) => (
            <div key={i} className="community-row community-row--skeleton" data-testid="community-skeleton">
              <Skeleton width="56px" height="56px" circle />
              <div className="community-row__text">
                <Skeleton width="60%" height="1rem" />
                <Skeleton width="35%" height="0.75rem" />
              </div>
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
            <div key={community.id} className="community-row">
              <Link to={communityPath(community)} className="community-row__link">
                <Avatar src={community.imageUrl} name={community.name} size="lg" />
                <span className="community-row__text">
                  <span className="community-row__name">{community.name}</span>
                  <span className="community-members">
                    {[community.city, `${community.memberCount} ${community.memberCount === 1 ? "miembro" : "miembros"}`].filter(Boolean).join(" · ")}
                  </span>
                </span>
              </Link>
              <div className="community-row__action">
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
            description={filtered ? "Prueba con otro lugar." : "Crea la primera y reúne a tu grupo."}
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
