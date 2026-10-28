import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { FaSearch, FaTimes, FaHistory } from "react-icons/fa";
import "./Search.css";
import { getAllUsersRequest } from "../../features/users/services/users.service";
import { getCommunitiesRequest } from "../../features/communities/services/communities.service";
import { getEventsRequest } from "../../features/events/services/events.service";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { useRecentSearches } from "../../features/search/hooks/useRecentSearches";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import CommunityRow from "../../features/search/components/CommunityRow";
import EventCard from "../../features/events/components/EventCard";
import UserCard from "../../shared/ui/UserCard";
import Tabs from "../../shared/ui/Tabs";
import EmptyState from "../../shared/ui/EmptyState";
import Skeleton from "../../shared/ui/Skeleton";
import Button from "../../shared/forms/Button";

const DEBOUNCE_MS = 300;
const ALL_PREVIEW = 3;
const SUGGESTIONS = 5;

const SEARCH_TABS = [
  { id: "all", label: "Todo" },
  { id: "users", label: "Personas" },
  { id: "communities", label: "Comunidades" },
  { id: "events", label: "Quedadas" },
];
const TAB_IDS = SEARCH_TABS.map((tab) => tab.id);

function UserRowSkeleton() {
  return (
    <div className="search-skeleton-row" data-testid="search-skeleton">
      <Skeleton width="48px" height="48px" circle />
      <div className="search-skeleton-row__lines">
        <Skeleton width="55%" height="1rem" />
        <Skeleton width="35%" height="0.75rem" />
      </div>
    </div>
  );
}

function EventSkeleton() {
  return (
    <div className="search-skeleton-event" data-testid="search-skeleton">
      <Skeleton height="104px" />
    </div>
  );
}

/** A block of results: title, rows (skeleton / error / items) and "Ver todas" or "Cargar más". */
function ResultSection({ title, section, render, skeleton, preview, onSeeAll, hideWhenEmpty }) {
  const { items, loading, loadingMore, hasMore, error, loadMore, retry } = section;
  if (hideWhenEmpty && !loading && !error && items.length === 0) return null;
  const shown = preview ? items.slice(0, preview) : items;

  return (
    <section className="search-section" aria-label={title} aria-busy={loading || undefined}>
      <h2 className="search-section__title">{title}</h2>
      {loading && [0, 1, 2].slice(0, preview || 3).map((i) => <div key={i}>{skeleton}</div>)}
      {!loading && error && (
        <div className="search-error" role="alert">
          <p>{error}</p>
          <Button variant="outline" size="small" onClick={retry}>Reintentar</Button>
        </div>
      )}
      {!loading && !error && shown.map(render)}
      {!loading && !error && preview && (items.length > preview || hasMore) && (
        <Button variant="ghost" className="search-section__more" onClick={onSeeAll}>Ver todas</Button>
      )}
      {!loading && !error && !preview && hasMore && (
        <Button variant="ghost" className="search-section__more" onClick={loadMore} loading={loadingMore}>
          Cargar más
        </Button>
      )}
    </section>
  );
}

const Search = () => {
  const { currentUser } = useAuth();
  const [params, setParams] = useSearchParams();
  const tabParam = params.get("tab");
  const tab = TAB_IDS.includes(tabParam) ? tabParam : "all";
  const qParam = params.get("q") ?? "";

  const [text, setText] = useState(qParam);
  const [query, setQuery] = useState(qParam.trim());
  // Search mode, as in Instagram: focusing the field swaps the suggestions for her recents until she cancels
  const [active, setActive] = useState(false);
  const inputRef = useRef(null);
  const { recents, add: addRecent, remove: removeRecent, clear: clearRecents } = useRecentSearches();

  // Search when she stops typing; the term lives in the URL so a reload or a shared link keeps it
  useEffect(() => {
    const id = setTimeout(() => {
      const clean = text.trim();
      setQuery(clean);
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (clean) next.set("q", clean);
          else next.delete("q");
          return next;
        },
        { replace: true },
      );
    }, DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [text, setParams]);

  const setTab = (id) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (id === "all") next.delete("tab");
        else next.set("tab", id);
        return next;
      },
      { replace: true },
    );

  const searching = query.length > 0;
  const wantsUsers = tab === "all" || tab === "users";
  const wantsCommunities = tab === "all" || tab === "communities";
  const wantsEvents = tab === "all" || tab === "events";

  const users = useSearchSection(
    async (page) => {
      const result = await getAllUsersRequest(page, 20, query);
      return { items: result?.content ?? [], hasMore: result?.last === false };
    },
    query,
    searching && wantsUsers,
  );
  // Both of these also feed the suggestions when there is no text
  const communities = useSearchSection(
    (page) => getCommunitiesRequest({ page, query }),
    query,
    wantsCommunities,
  );
  const events = useSearchSection(
    (page) => getEventsRequest({ page, q: query, from: new Date().toISOString(), size: searching ? 20 : SUGGESTIONS }),
    query,
    wantsEvents,
  );

  const myUsername = currentUser?.username;

  const renderUser = (user) => (
    <UserCard
      key={user.username}
      fullName={user.fullName}
      username={user.username}
      profilePictureUrl={user.profilePictureUrl}
      showFollowButton={myUsername !== user.username}
      isFollowing={Boolean(user.isFollowing)}
    />
  );
  const renderCommunity = (community) => <CommunityRow key={community.id} community={community} />;
  const renderEvent = (event) => (
    <div className="search-event" key={event.id}>
      <EventCard event={event} />
    </div>
  );

  const onSubmit = (e) => {
    e.preventDefault();
    setQuery(text.trim());
    addRecent(text);
  };

  const pickRecent = (term) => {
    setText(term);
    setQuery(term);
  };

  const clearText = () => {
    setText("");
    inputRef.current?.focus();
  };

  const cancel = () => {
    setText("");
    setQuery("");
    setActive(false);
    inputRef.current?.blur();
  };

  const showRecents = active && !text.trim();

  // Tapping any result keeps the term in her recents
  const rememberOnResultClick = () => addRecent(query);

  const settledEmpty = (s) => !s.loading && !s.error && s.items.length === 0;
  const nothingFound =
    searching && (tab === "all" ? [users, communities, events].every(settledEmpty) : settledEmpty({ users, communities, events }[tab]));

  const sectionProps = (preview) => ({ preview, hideWhenEmpty: tab === "all" });

  return (
    <div className="search-page">
      <div className="search-top">
        <div className="search-bar">
          <form className="search-field" role="search" onSubmit={onSubmit}>
            <FaSearch className="search-field__icon" aria-hidden="true" />
            <input
              ref={inputRef}
              type="search"
              enterKeyHint="search"
              placeholder="Buscar"
              aria-label="Buscar"
              autoComplete="off"
              value={text}
              onFocus={() => setActive(true)}
              onKeyDown={(e) => e.key === "Escape" && cancel()}
              onChange={(e) => setText(e.target.value)}
            />
            {text && (
              <button type="button" className="search-field__clear" aria-label="Borrar búsqueda" onClick={clearText}>
                <FaTimes aria-hidden="true" />
              </button>
            )}
          </form>
          {(active || text) && (
            <Button variant="ghost" className="search-bar__cancel" onClick={cancel}>Cancelar</Button>
          )}
        </div>
        {!showRecents && (
          <Tabs tabs={SEARCH_TABS} value={tab} onChange={setTab} label="Tipo de resultado" idPrefix="search-tab" />
        )}
      </div>

      {showRecents && (
        <div className="search-results">
          <section className="search-section" aria-label="Búsquedas recientes">
            <div className="search-section__head">
              <h2 className="search-section__title">Recientes</h2>
              {recents.length > 0 && (
                <Button variant="ghost" size="small" onClick={clearRecents}>Borrar todo</Button>
              )}
            </div>
            {recents.length === 0 && <p className="search-hint">No hay búsquedas recientes.</p>}
            <ul className="search-recents">
              {recents.map((term) => (
                <li key={term} className="search-recents__item">
                  <button type="button" className="search-recents__term" onClick={() => pickRecent(term)}>
                    <FaHistory aria-hidden="true" />
                    <span>{term}</span>
                  </button>
                  <button
                    type="button"
                    className="search-recents__remove"
                    aria-label={`Quitar ${term} de recientes`}
                    onClick={() => removeRecent(term)}
                  >
                    <FaTimes aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          </section>
        </div>
      )}

      {!showRecents && (
        <div className="search-results" id={`search-tab-panel-${tab}`} role="tabpanel" aria-labelledby={`search-tab-${tab}`}
          onClick={searching ? rememberOnResultClick : undefined}>
          {!searching && (
            <>
              {tab === "users" && (
                <EmptyState title="Busca a otras corredoras" description="Escribe un nombre o un @usuario." />
              )}

              {wantsCommunities && (
                <ResultSection
                  title="Comunidades para ti"
                  section={{ ...communities, items: communities.items.filter((c) => !c.role).slice(0, SUGGESTIONS), hasMore: false }}
                  render={renderCommunity}
                  skeleton={<UserRowSkeleton />}
                  hideWhenEmpty
                />
              )}
              {wantsEvents && (
                <ResultSection
                  title="Próximas quedadas"
                  section={{ ...events, hasMore: false }}
                  render={renderEvent}
                  skeleton={<EventSkeleton />}
                  hideWhenEmpty
                />
              )}
            </>
          )}

          {searching && (
            <>
              {wantsUsers && (
                <ResultSection
                  title="Personas"
                  section={users}
                  render={renderUser}
                  skeleton={<UserRowSkeleton />}
                  onSeeAll={() => setTab("users")}
                  {...sectionProps(tab === "all" ? ALL_PREVIEW : undefined)}
                />
              )}
              {wantsCommunities && (
                <ResultSection
                  title="Comunidades"
                  section={communities}
                  render={renderCommunity}
                  skeleton={<UserRowSkeleton />}
                  onSeeAll={() => setTab("communities")}
                  {...sectionProps(tab === "all" ? ALL_PREVIEW : undefined)}
                />
              )}
              {wantsEvents && (
                <ResultSection
                  title="Quedadas"
                  section={events}
                  render={renderEvent}
                  skeleton={<EventSkeleton />}
                  onSeeAll={() => setTab("events")}
                  {...sectionProps(tab === "all" ? ALL_PREVIEW : undefined)}
                />
              )}
              {nothingFound && (
                <EmptyState
                  title={`No hay resultados para «${query}»`}
                  description={tab === "events" ? "Prueba con otro título, ciudad o lugar." : "Prueba con otro nombre."}
                  action={<Button variant="outline" onClick={() => setText("")}>Borrar búsqueda</Button>}
                />
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default Search;
