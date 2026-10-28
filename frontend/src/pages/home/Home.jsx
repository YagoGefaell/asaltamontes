import "./Home.css";
import { useContext, useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FaBell, FaEnvelope, FaMountain } from "react-icons/fa";
import PageHeader from "../../shared/ui/PageHeader";
import EmptyState from "../../shared/ui/EmptyState";
import Skeleton from "../../shared/ui/Skeleton";
import Avatar from "../../shared/ui/Avatar";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import Button from "../../shared/forms/Button";
import EventCard from "../../features/events/components/EventCard";
import { useEvents } from "../../features/events/hooks/useEvents";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import { getCommunitiesRequest } from "../../features/communities/services/communities.service";
import { communityPath } from "../../features/communities/utils/communityPath";
import { ConversationsContext } from "../../features/messages/hooks/conversations.context";
import { useNotifications } from "../../features/notifications/hooks/useNotifications";
import logo from "../../assets/logo01.png";

const formatCount = (n) => (n > 9 ? "9+" : n);
const MY_EVENTS = 3;
const UPCOMING_EVENTS = 5;
const SUGGESTED_COMMUNITIES = 10;

function HeaderLink({ to, label, count, children }) {
  return (
    <Link to={to} className="home-header__link" aria-label={count > 0 ? `${label}, ${count} sin leer` : label}>
      {children}
      {count > 0 && (
        <span className="home-header__badge" aria-hidden="true">{formatCount(count)}</span>
      )}
    </Link>
  );
}

function Section({ title, to, linkLabel, children }) {
  return (
    <section className="home-section" aria-label={title}>
      {title && (
        <div className="home-section__head">
          <h2 className="home-section__title">{title}</h2>
          {to && <Link to={to} className="home-section__link">{linkLabel}</Link>}
        </div>
      )}
      {children}
    </section>
  );
}

/** Horizontal row of recommended communities, shown between blocks of content. */
function RecommendedCommunities({ items }) {
  return (
    <Section title="Comunidades recomendadas" to="/search?tab=communities" linkLabel="Ver más">
      <ul className="home-rail">
        {items.map((community) => (
          <li key={community.id} className="home-rail__item">
            <Link to={communityPath(community)} className="home-rail__link">
              <Avatar src={community.imageUrl} name={community.name} size="lg" />
              <span className="home-rail__name">{community.name}</span>
              <span className="home-rail__meta">{community.city || `${community.memberCount} miembros`}</span>
            </Link>
            {community.visibility === "PRIVATE" && !community.role ? (
              <CommunityRequestButton communityId={community.id} />
            ) : (
              <CommunityJoinButton communityId={community.id} role={community.role} joinLabel="Unirse" memberLabel="Miembro" />
            )}
          </li>
        ))}
      </ul>
    </Section>
  );
}

function EventSkeleton() {
  return (
    <div className="home-skeleton" data-testid="home-skeleton">
      <Skeleton height="104px" />
    </div>
  );
}

const Home = () => {
  const navigate = useNavigate();
  const { totalUnreadCount } = useContext(ConversationsContext);
  const { unreadCount: unreadNotifications } = useNotifications();

  // Upcoming events: the ones she is going to, and the rest she can see (public and her communities')
  const mineFilters = useMemo(() => ({ from: new Date().toISOString(), mine: true, size: MY_EVENTS }), []);
  const upcomingFilters = useMemo(() => ({ from: new Date().toISOString(), size: UPCOMING_EVENTS + MY_EVENTS }), []);
  const mine = useEvents(mineFilters);
  const upcoming = useEvents(upcomingFilters);

  const suggested = useSearchSection(
    async (page) => {
      const result = await getCommunitiesRequest({ page });
      return { items: result.items.filter((c) => !c.role).slice(0, SUGGESTED_COMMUNITIES), hasMore: false };
    },
    "suggested",
  );

  const replaceIn = (list) => (updated) =>
    list.setEvents((prev) => prev.map((e) => (e.id === updated.id ? { ...e, ...updated } : e)));

  const mineIds = new Set(mine.events.map((e) => e.id));
  const others = upcoming.events.filter((e) => !mineIds.has(e.id)).slice(0, UPCOMING_EVENTS);

  const loading = mine.loading || upcoming.loading || suggested.loading;
  const failed = Boolean(mine.error || upcoming.error);
  const nothing = !loading && !failed
    && mine.events.length === 0 && others.length === 0
    && suggested.items.length === 0;

  return (
    <div className="home-container">
      <PageHeader
        title={<img className="home-header__logo" src={logo} alt="Asaltamontes Female" />}
        actions={
          <>
            <HeaderLink to="/notifications" label="Notificaciones" count={unreadNotifications}>
              <FaBell aria-hidden="true" />
            </HeaderLink>
            <HeaderLink to="/messages" label="Mensajes" count={totalUnreadCount}>
              <FaEnvelope aria-hidden="true" />
            </HeaderLink>
          </>
        }
      />

      {mine.loading && (
        <Section title="Tus próximas quedadas"><EventSkeleton /></Section>
      )}
      {!mine.loading && mine.events.length > 0 && (
        <Section title="Tus próximas quedadas" to="/events" linkLabel="Ver todas">
          {mine.events.map((event) => (
            <div className="home-event" key={event.id}>
              <EventCard event={event} onChange={replaceIn(mine)} />
            </div>
          ))}
        </Section>
      )}

      {upcoming.loading && (
        <Section title="Próximas quedadas"><EventSkeleton /><EventSkeleton /></Section>
      )}
      {!upcoming.loading && others.length > 0 && (
        <Section title="Próximas quedadas" to="/events" linkLabel="Ver más">
          {others.slice(0, 2).map((event) => (
            <div className="home-event" key={event.id}>
              <EventCard event={event} onChange={replaceIn(upcoming)} />
            </div>
          ))}
        </Section>
      )}

      {!suggested.loading && suggested.items.length > 0 && <RecommendedCommunities items={suggested.items} />}

      {!upcoming.loading && others.length > 2 && (
        <Section>
          {others.slice(2).map((event) => (
            <div className="home-event" key={event.id}>
              <EventCard event={event} onChange={replaceIn(upcoming)} />
            </div>
          ))}
        </Section>
      )}

      {failed && !loading && (
        <Section title="Próximas quedadas">
          <p className="home-error" role="alert">No hemos podido cargar las quedadas.</p>
          <Button
            variant="ghost"
            onClick={() => {
              mine.refetch();
              upcoming.refetch();
            }}
          >
            Reintentar
          </Button>
        </Section>
      )}

      {nothing && (
        <EmptyState
          icon={<FaMountain />}
          title="Todavía no hay quedadas a la vista"
          description="Únete a una comunidad o busca una quedada cerca de ti para empezar."
          action={<Button variant="primary" onClick={() => navigate("/events")}>Ver quedadas</Button>}
        />
      )}
    </div>
  );
};

export default Home;
