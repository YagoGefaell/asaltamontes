import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { FaCalendarAlt, FaUsers } from "react-icons/fa";
import EmptyState from "../../shared/ui/EmptyState";
import Skeleton from "../../shared/ui/Skeleton";
import Button from "../../shared/forms/Button";
import EventCard from "../../features/events/components/EventCard";
import CommunityRow from "../../features/search/components/CommunityRow";
import { useEvents } from "../../features/events/hooks/useEvents";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import { getMyCommunitiesRequest } from "../../features/communities/services/communities.service";

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

/** "Quedadas" tab of her own profile: the upcoming events she is going to or organizes. */
export function ProfileEvents() {
  const navigate = useNavigate();
  const filters = useMemo(() => ({ from: new Date().toISOString(), mine: true }), []);
  const { events, setEvents, loading, loadingMore, hasMore, loadMore, error, refetch } = useEvents(filters);
  const replace = (updated) =>
    setEvents((prev) => prev.map((e) => (e.id === updated.id ? { ...e, ...updated } : e)));

  return (
    <div className="pf-panel" role="tabpanel" id="profile-panel-events" aria-labelledby="profile-events" aria-busy={loading || undefined}>
      {loading && <Rows height="104px" />}
      {!loading && error && <ErrorBox message={error} onRetry={refetch} />}
      {!loading && !error && events.length === 0 && (
        <EmptyState
          icon={<FaCalendarAlt />}
          title="Aún no tienes quedadas"
          description="Apúntate a una y aparecerá aquí."
          action={<Button variant="primary" onClick={() => navigate("/events")}>Ver quedadas</Button>}
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
export function ProfileCommunities() {
  const navigate = useNavigate();
  const { items, loading, loadingMore, hasMore, error, loadMore, retry } = useSearchSection(
    (page) => getMyCommunitiesRequest({ page }),
    "mine",
  );

  return (
    <div className="pf-panel" role="tabpanel" id="profile-panel-communities" aria-labelledby="profile-communities" aria-busy={loading || undefined}>
      {loading && <Rows count={3} height="64px" />}
      {!loading && error && <ErrorBox message={error} onRetry={retry} />}
      {!loading && !error && items.length === 0 && (
        <EmptyState
          icon={<FaUsers />}
          title="Aún no estás en ninguna comunidad"
          description="Únete a una para correr acompañada."
          action={<Button variant="primary" onClick={() => navigate("/communities")}>Descubrir comunidades</Button>}
        />
      )}
      {!loading && !error && items.map((community) => <CommunityRow key={community.id} community={community} />)}
      {!loading && !error && hasMore && (
        <Button variant="ghost" className="pf-more" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
      )}
    </div>
  );
}
