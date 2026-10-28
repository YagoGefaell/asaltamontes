import "./Home.css";
import { useContext } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FaBell, FaEnvelope, FaPen, FaRegImages } from "react-icons/fa";
import PageHeader from "../../shared/ui/PageHeader";
import EmptyState from "../../shared/ui/EmptyState";
import Avatar from "../../shared/ui/Avatar";
import { useToast } from "../../shared/ui/Toast";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import Button from "../../shared/forms/Button";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import FollowUserButton from "../../shared/components/FollowUserButton";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { getSuggestionsRequest } from "../../features/users/services/users.service";
import { getCommunitiesRequest } from "../../features/communities/services/communities.service";
import { communityPath } from "../../features/communities/utils/communityPath";
import { ConversationsContext } from "../../features/messages/hooks/conversations.context";
import { useNotifications } from "../../features/notifications/hooks/useNotifications";
import logo from "../../assets/logo01.png";

const formatCount = (n) => (n > 9 ? "9+" : n);
const SUGGESTED_COMMUNITIES = 10;
const SUGGESTED_USERS = 10;
const POSTS_SOON = "Las publicaciones llegan pronto";

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

/** Horizontal row of recommended communities, shown between blocks of content. */
function RecommendedCommunities({ items }) {
  return (
    <section className="home-section" aria-label="Comunidades recomendadas">
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
    </section>
  );
}

/** Horizontal row of users she may want to follow (followed by her follows, or in her communities). */
function SuggestedUsers({ items }) {
  return (
    <section className="home-section" aria-label="Usuarias para seguir">
      <ul className="home-rail">
        {items.map((user) => (
          <li key={user.username} className="home-rail__item">
            <Link to={`/users/${user.username}`} className="home-rail__link">
              <Avatar src={user.profilePictureUrl} name={user.fullName || user.username} size="lg" />
              <span className="home-rail__name">{user.fullName || user.username}</span>
              <span className="home-rail__meta">@{user.username}</span>
            </Link>
            <FollowUserButton userId={user.username} initialIsFollowing={false} />
          </li>
        ))}
      </ul>
    </section>
  );
}

/** Preview of the post composer (T22): looks like the real one, but posting is not available yet. */
function Composer() {
  const { currentUser } = useAuth();
  const toast = useToast();
  const name = currentUser?.fullName || currentUser?.username || "";

  return (
    <div className="home-composer">
      <Avatar src={currentUser?.profilePictureUrl} name={name} size="md" />
      <button type="button" className="home-composer__field" onClick={() => toast.info(POSTS_SOON)}>
        ¿Qué tal tu último entreno?
      </button>
      <button type="button" className="home-composer__photo" aria-label="Añadir foto" onClick={() => toast.info(POSTS_SOON)}>
        <FaRegImages aria-hidden="true" />
      </button>
    </div>
  );
}

// Home is for posts (T22), with suggestions between them. Until posts exist: composer preview,
// an empty feed and the suggestion rows. Events live in their own tab.
const Home = () => {
  const navigate = useNavigate();
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
            <HeaderLink to="/notifications" label="Notificaciones" count={unreadNotifications}>
              <FaBell aria-hidden="true" />
            </HeaderLink>
            <HeaderLink to="/messages" label="Mensajes" count={totalUnreadCount}>
              <FaEnvelope aria-hidden="true" />
            </HeaderLink>
          </>
        }
      />

      <Composer />

      <EmptyState
        icon={<FaPen />}
        title="Aún no hay publicaciones"
        description="Aquí verás lo que comparten tus comunidades y las corredoras que sigues."
        action={<Button variant="secondary" onClick={() => navigate("/search?tab=communities")}>Explorar comunidades</Button>}
      />

      {!people.loading && people.items.length > 0 && <SuggestedUsers items={people.items} />}
      {!suggested.loading && suggested.items.length > 0 && <RecommendedCommunities items={suggested.items} />}
    </div>
  );
};

export default Home;
