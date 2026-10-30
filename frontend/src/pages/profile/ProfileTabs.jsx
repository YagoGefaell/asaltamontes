import { useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { FaCalendarAlt, FaUsers } from "react-icons/fa";
import EmptyState from "../../shared/ui/EmptyState";
import Skeleton from "../../shared/ui/Skeleton";
import Button from "../../shared/forms/Button";
import PostsTab from "../../features/posts/components/PostsTab";
import { getUserPostsRequest } from "../../features/posts/services/posts.service";
import EventCard from "../../features/events/components/EventCard";
import CommunityRow from "../../features/search/components/CommunityRow";
import { useEvents } from "../../features/events/hooks/useEvents";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import { getUserEventsRequest } from "../../features/events/services/events.service";
import { getMyCommunitiesRequest, getUserCommunitiesRequest } from "../../features/communities/services/communities.service";

function Rows({ count = 2, height }) {
  return (
    <div className="pf-skeletons" data-testid="profile-skeleton">
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

/** "Publicaciones" tab (T22b), first of the profile: hers or another user's, as a list of full publications. */
export function ProfilePosts({ username, isOwn = false }) {
  const fetchPage = useCallback((before) => getUserPostsRequest(username, before), [username]);
  return (
    <PostsTab
      panelId="profile-panel-posts"
      tabId="profile-posts"
      fetchPage={fetchPage}
      emptyTitle={isOwn ? "Aún no has publicado nada" : "Aún no hay publicaciones"}
      emptyDescription={isOwn ? "Lo que publiques desde Inicio aparecerá aquí." : "Cuando publique algo, aparecerá aquí."}
    />
  );
}

/** "Quedadas" tab of her own profile: the upcoming events she is going to or organizes. */
export function ProfileEvents({ username = null }) {
  const navigate = useNavigate();
  const filters = useMemo(() => ({ from: new Date().toISOString(), mine: true }), []);
  const ownEvents = useEvents(filters, !username);
  // Another user's profile: what she organizes and I can see (never her attendance, D-023)
  const theirs = useSearchSection(
    (page) => getUserEventsRequest(username, { page, from: new Date().toISOString() }),
    `events:${username}`,
    Boolean(username),
  );
  const { events, setEvents, loading, loadingMore, hasMore, loadMore, error, refetch } = username
    ? { events: theirs.items, setEvents: () => {}, loading: theirs.loading, loadingMore: theirs.loadingMore,
        hasMore: theirs.hasMore, loadMore: theirs.loadMore, error: theirs.error, refetch: theirs.retry }
    : ownEvents;
  const replace = (updated) =>
    setEvents((prev) => prev.map((e) => (e.id === updated.id ? { ...e, ...updated } : e)));

  return (
    <div className="pf-panel" role="tabpanel" id="profile-panel-events" aria-labelledby="profile-events" aria-busy={loading || undefined}>
      {loading && <Rows height="104px" />}
      {!loading && error && <ErrorBox message={error} onRetry={refetch} />}
      {!loading && !error && events.length === 0 && (
        <EmptyState
          icon={<FaCalendarAlt />}
          title={username ? "No hay quedadas a la vista" : "Aún no tienes quedadas"}
          description={username ? "Cuando organice una que puedas ver, aparecerá aquí." : "Apúntate a una y aparecerá aquí."}
          action={username ? undefined : <Button variant="primary" onClick={() => navigate("/events")}>Ver quedadas</Button>}
        />
      )}
      {!loading && !error && events.map((event) => (
        <div className="pf-item" key={event.id}>
          <EventCard event={event} onChange={replace} />
        </div>
      ))}
      {!loading && !error && hasMore && (
        <Button variant="ghost" className="pf-more" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
      )}
    </div>
  );
}

/** "Comunidades" tab of her own profile. */
export function ProfileCommunities({ username = null }) {
  const navigate = useNavigate();
  const { items, loading, loadingMore, hasMore, error, loadMore, retry } = useSearchSection(
    (page) => (username ? getUserCommunitiesRequest(username, { page }) : getMyCommunitiesRequest({ page })),
    username ? `communities:${username}` : "mine",
  );

  return (
    <div className="pf-panel" role="tabpanel" id="profile-panel-communities" aria-labelledby="profile-communities" aria-busy={loading || undefined}>
      {loading && <Rows count={3} height="64px" />}
      {!loading && error && <ErrorBox message={error} onRetry={retry} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState
          icon={<FaUsers />}
          title={username ? "Sin comunidades públicas" : "Aún no estás en ninguna comunidad"}
          description={username ? "Aquí solo se ven sus comunidades públicas." : "Únete a una para correr acompañada."}
          action={username ? undefined : <Button variant="primary" onClick={() => navigate("/communities")}>Descubrir comunidades</Button>}
        />
      )}
      {!loading && !error && items.map((community) => <CommunityRow key={community.id} community={community} />)}
      {!loading && !error && hasMore && (
        <Button variant="ghost" className="pf-more" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
      )}
    </div>
  );
}
