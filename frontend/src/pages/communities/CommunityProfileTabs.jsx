import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { FaCalendarAlt, FaLock, FaUsers } from "react-icons/fa";
import Button from "../../shared/forms/Button";
import EmptyState from "../../shared/ui/EmptyState";
import SearchField from "../../shared/ui/SearchField";
import Skeleton from "../../shared/ui/Skeleton";
import PostsTab from "../../features/posts/components/PostsTab";
import { getCommunityPostsRequest } from "../../features/posts/services/posts.service";
import UserCard from "../../shared/ui/UserCard";
import EventCard from "../../features/events/components/EventCard";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import { getCommunityMembersPageRequest } from "../../features/communities/services/communities.service";
import { isStaff } from "../../features/communities/utils/communityErrors";

// Above this many members the list gets a search field
export const MEMBERS_SEARCH_THRESHOLD = 20;

function Rows({ count = 2, height }) {
  return (
    <div className="pf-skeletons" data-testid="community-skeleton">
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} height={height} />
      ))}
    </div>
  );
}

function ErrorBox({ message, onRetry }) {
  return (
    <div className="pf-error" role="alert">
      <p>{message}</p>
      <Button variant="outline" size="small" onClick={onRetry}>Reintentar</Button>
    </div>
  );
}

/** "Publicaciones" tab (T22b), first of the community. Owner and moderators publish from the create screen (D-025). */
export function CommunityPostsTab({ community }) {
  const slug = community.slug || community.name;
  const fetchPage = useCallback((before) => getCommunityPostsRequest(slug, before), [slug]);
  return (
    <PostsTab
      panelId="community-panel-posts"
      tabId="community-posts"
      fetchPage={fetchPage}
      emptyTitle="Todavía no hay publicaciones"
    />
  );
}

/** "Quedadas" tab: upcoming events of the community (the page loads the list, which also feeds its counter). */
export function CommunityEventsTab({ community, section }) {
  const navigate = useNavigate();
  const { items, loading, loadingMore, hasMore, error, loadMore, retry } = section;
  const canCreate = isStaff(community.role);
  const createButton = (
    <Button size="small" onClick={() => navigate(`/events/new?community=${community.id}`)}>Crear quedada</Button>
  );

  return (
    <div className="pf-panel" role="tabpanel" id="community-panel-events" aria-labelledby="community-events" aria-busy={loading || undefined}>
      {canCreate && items.length > 0 && (
        <div className="pf-item">{createButton}</div>
      )}
      {loading && <Rows height="104px" />}
      {!loading && error && <ErrorBox message={error} onRetry={retry} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState
          icon={<FaCalendarAlt />}
          title="Todavía no hay quedadas programadas"
          action={canCreate ? createButton : undefined}
        />
      )}
      {!loading && !error && items.map((event) => (
        <div className="pf-item" key={event.id}>
          <EventCard event={event} />
        </div>
      ))}
      {!loading && !error && hasMore && (
        <Button variant="ghost" className="pf-more" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
      )}
    </div>
  );
}

/** "Miembros" tab: UserCard rows, paged; only members get the list (BUG-066). */
export function CommunityMembersTab({ community }) {
  const isMember = Boolean(community.role);
  const [query, setQuery] = useState("");
  const { items, loading, loadingMore, hasMore, error, loadMore, retry } = useSearchSection(
    (page) => getCommunityMembersPageRequest(community.id, page),
    `community-members:${community.id}`,
    isMember,
  );

  const needle = query.trim().toLowerCase().replace(/^@/, "");
  const visible = useMemo(
    () => (needle
      ? items.filter((m) => m.username.toLowerCase().includes(needle) || (m.fullName || "").toLowerCase().includes(needle))
      : items),
    [items, needle],
  );
  const showSearch = community.memberCount > MEMBERS_SEARCH_THRESHOLD;
  const ready = isMember && !loading && !error;

  return (
    <div className="pf-panel" role="tabpanel" id="community-panel-members" aria-labelledby="community-members" aria-busy={loading || undefined}>
      {!isMember && (
        <EmptyState icon={<FaLock />} title="Solo las miembros ven la lista" description="Únete a la comunidad para ver quién forma parte." />
      )}
      {isMember && showSearch && (
        <div className="pf-item">
          <SearchField value={query} onChange={setQuery} onClear={() => setQuery("")} label="Buscar miembros" />
        </div>
      )}
      {isMember && loading && <Rows count={3} height="56px" />}
      {isMember && !loading && error && <ErrorBox message={error} onRetry={retry} />}
      {ready && items.length === 0 && <EmptyState icon={<FaUsers />} title="Aún no hay más miembros" />}
      {ready && items.length > 0 && visible.length === 0 && (
        <p className="pf-error">Ninguna miembro coincide con la búsqueda.</p>
      )}
      {ready && visible.map((member) => (
        <UserCard
          key={member.username}
          fullName={member.fullName}
          username={member.username}
          profilePictureUrl={member.profilePictureUrl}
        />
      ))}
      {ready && hasMore && (
        <Button variant="ghost" className="pf-more" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
      )}
    </div>
  );
}
