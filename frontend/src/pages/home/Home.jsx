import "./Home.css";
import { useContext } from "react";
import { Link } from "react-router-dom";
import { FaBell, FaComment, FaPen, FaPlus } from "react-icons/fa";
import PageHeader from "../../shared/ui/PageHeader";
import EmptyState from "../../shared/ui/EmptyState";
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

// Home is for posts (T22), with suggestions between them. Until posts exist: an empty feed and
// the suggestion rows. Events live in their own tab; creating a post will be its own screen.
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

      <EmptyState
        icon={<FaPen />}
        title="Aún no hay publicaciones"
        description="Aquí verás lo que comparten tus comunidades y las corredoras que sigues."
      />

      {!people.loading && people.items.length > 0 && <SuggestedUsers items={people.items} />}
      {!suggested.loading && suggested.items.length > 0 && <RecommendedCommunities items={suggested.items} />}
    </div>
  );
};

export default Home;
