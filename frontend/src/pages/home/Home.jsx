import "./Home.css";
import { Fragment, useContext } from "react";
import { Link } from "react-router-dom";
import { FaBell, FaComment, FaPen, FaPlus } from "react-icons/fa";
import PageHeader from "../../shared/ui/PageHeader";
import EmptyState from "../../shared/ui/EmptyState";
import Button from "../../shared/forms/Button";
import Skeleton from "../../shared/ui/Skeleton";
import PostCard from "../../features/posts/components/PostCard";
import { getFeedRequest } from "../../features/posts/services/posts.service";
import { useLoadMoreOnView } from "../../features/posts/hooks/useLoadMoreOnView";
import { useCursorList } from "../../features/communities/hooks/useCursorList";
import Avatar from "../../shared/ui/Avatar";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import FollowUserButton from "../../shared/components/FollowUserButton";
import { getSuggestionsRequest } from "../../features/users/services/users.service";
import { getCommunitiesRequest } from "../../features/communities/services/communities.service";
import { communityPath } from "../../features/communities/utils/communityPath";
import { ConversationsContext } from "../../features/messages/hooks/conversations.context";
import { useNotifications } from "../../features/notifications/hooks/useNotifications";
import logo from "../../assets/logo01.png";

const formatCount = (n) => (n > 9 ? "9+" : n);
const SUGGESTED_COMMUNITIES = 10;
const SUGGESTED_USERS = 10;
// A suggestions row (users, then communities, alternating) after every this many posts
const POSTS_PER_ROW = 5;

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

// Between Avatar's lg (64) and xl (96): the picture leads the card without filling it
const SUGGESTION_AVATAR = 64;

function Section({ title, children }) {
  return (
    <section className="home-section" aria-label={title}>
      <h2 className="home-section__title">{title}</h2>
      {children}
    </section>
  );
}

/** Horizontal row of recommended communities, shown between blocks of content. */
function RecommendedCommunities({ items }) {
  return (
    <Section title="Comunidades recomendadas">
      <ul className="home-rail">
        {items.map((community) => (
          <li key={community.id} className="home-rail__item home-rail__item--community">
            <Link to={communityPath(community)} className="home-rail__link">
              <Avatar src={community.imageUrl} name={community.name} size={SUGGESTION_AVATAR} />
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

/** Horizontal row of users she may want to follow (followed by her follows, or in her communities). */
function SuggestedUsers({ items }) {
  return (
    <Section title="Usuarias para seguir">
      <ul className="home-rail">
        {items.map((user) => (
          <li key={user.username} className="home-rail__item">
            <Link to={`/users/${user.username}`} className="home-rail__link">
              <Avatar src={user.profilePictureUrl} name={user.fullName || user.username} size={SUGGESTION_AVATAR} />
              <span className="home-rail__name">{user.fullName || user.username}</span>
              <span className="home-rail__meta">@{user.username}</span>
            </Link>
            <FollowUserButton userId={user.username} initialIsFollowing={false} />
          </li>
        ))}
      </ul>
    </Section>
  );
}

// Home is the feed of posts (T22c) with a suggestions row every POSTS_PER_ROW posts. With fewer
// posts than one row, the rows close the page. Events live in their own tab.
const Home = () => {
  const { totalUnreadCount } = useContext(ConversationsContext);
  const { unreadCount: unreadNotifications } = useNotifications();

  const people = useSearchSection(() => getSuggestionsRequest({ size: SUGGESTED_USERS }).then((r) => ({ ...r, hasMore: false })), "people");
  const suggested = useSearchSection(
    async (page) => {
      const result = await getCommunitiesRequest({ page });
      return { items: result.items.filter((c) => !c.role).slice(0, SUGGESTED_COMMUNITIES), hasMore: false };
    },
    "suggested",
  );

  const { items: posts, loading, loadingMore, error, hasMore, loadMore, reload, removeItem } = useCursorList(getFeedRequest);
  const sentinelRef = useLoadMoreOnView(loadMore, hasMore && !loadingMore);
  const hasPeople = !people.loading && people.items.length > 0;
  const hasCommunities = !suggested.loading && suggested.items.length > 0;

  // Rows go users, communities, users... ; if one kind is missing the other fills its turn
  const rowAfter = (index) => {
    if ((index + 1) % POSTS_PER_ROW !== 0) return null;
    const usersTurn = (index + 1) / POSTS_PER_ROW % 2 === 1;
    const showUsers = hasPeople && (usersTurn || !hasCommunities);
    if (showUsers) return <SuggestedUsers items={people.items} />;
    return hasCommunities ? <RecommendedCommunities items={suggested.items} /> : null;
  };
  const rowsInFeed = Math.floor(posts.length / POSTS_PER_ROW);
  const closingRows = !loading && !error && !hasMore && rowsInFeed === 0;

  return (
    <div className="home-container">
      <PageHeader
        title={<img className="home-header__logo" src={logo} alt="Asaltamontes Female" />}
        actions={
          <>
            <HeaderLink to="/posts/new" label="Nueva publicación" count={0}>
              <FaPlus aria-hidden="true" />
            </HeaderLink>
            <HeaderLink to="/notifications" label="Notificaciones" count={unreadNotifications}>
              <FaBell aria-hidden="true" />
            </HeaderLink>
            <HeaderLink to="/messages" label="Mensajes" count={totalUnreadCount}>
              <FaComment aria-hidden="true" />
            </HeaderLink>
          </>
        }
      />

      {loading && (
        <div className="home-skeletons" data-testid="home-skeleton">
          <Skeleton height="260px" />
          <Skeleton height="260px" />
        </div>
      )}
      {!loading && error && (
        <div className="home-error" role="alert">
          <p>{error.message || "No se ha podido cargar el inicio"}</p>
          <Button variant="outline" size="small" onClick={reload}>Reintentar</Button>
        </div>
      )}
      {!loading && !error && posts.length === 0 && (
        <EmptyState
          icon={<FaPen />}
          title="Aún no hay publicaciones"
          description="Aquí verás lo que comparten tus comunidades y las corredoras que sigues."
        />
      )}
      {!loading && !error && posts.map((post, index) => (
        <Fragment key={post.id}>
          <PostCard post={post} onDeleted={(deleted) => removeItem((item) => item.id === deleted.id)} />
          {rowAfter(index)}
        </Fragment>
      ))}
      {!loading && !error && hasMore && (
        <div ref={sentinelRef} className="home-more">
          <Button variant="ghost" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
        </div>
      )}

      {closingRows && hasPeople && <SuggestedUsers items={people.items} />}
      {closingRows && hasCommunities && <RecommendedCommunities items={suggested.items} />}
    </div>
  );
};

export default Home;
