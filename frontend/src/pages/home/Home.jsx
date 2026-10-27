import "./Home.css";
import { useContext, useState } from "react";
import { Link } from "react-router-dom";
import { FaBell, FaEnvelope } from "react-icons/fa";
import PageHeader from "../../shared/ui/PageHeader";
import { ConversationsContext } from "../../features/messages/hooks/conversations.context";
import { useNotifications } from "../../features/notifications/hooks/useNotifications";
import logo from "../../assets/logo01.png";

const formatCount = (n) => (n > 9 ? "9+" : n);

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

const Home = () => {
  const { totalUnreadCount } = useContext(ConversationsContext);
  const { unreadCount: unreadNotifications } = useNotifications();
  const [posts] = useState([
    {
      id: 1,
      user: "asaltamontes",
      content: "Comunidad femenina creciendo juntas 💖",
      likes: 120,
      liked: false,
    },
    {
      id: 2,
      user: "empowered_woman",
      content: "Celebrando nuestras historias y logros. ¡Somos increíbles! 💪",
      likes: 85,
      liked: false,
    },
    {
      id: 3,
      user: "creative_soul",
      content: "La creatividad y la solidaridad nos hacen fuertes",
      likes: 156,
      liked: false,
    },
  ]);

  const [likedPosts, setLikedPosts] = useState({});

  const handleLike = (postId) => {
    setLikedPosts((prev) => ({
      ...prev,
      [postId]: !prev[postId],
    }));
  };

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
      <div className="stories">
        {[1, 2, 3, 4, 5].map((story) => (
          <div className="story" key={story}>
            <div className="story-avatar" />
            <span className="story-name">User {story}</span>
          </div>
        ))}
      </div>

      <div className="feed">
        {posts.map((post) => (
          <div className="post-card" key={post.id}>
            <div className="post-header">
              <div className="post-user">
                <div className="post-avatar" />
                <span className="post-username">{post.user}</span>
              </div>
              <span className="post-menu">⋯</span>
            </div>

            <div className="post-image" />

            <div className="post-actions">
              <button
                className={`action-btn ${likedPosts[post.id] ? "liked" : ""}`}
                onClick={() => handleLike(post.id)}
              >
                {likedPosts[post.id] ? "❤️" : "🤍"}
              </button>
              <button className="action-btn">💬</button>
              <button className="action-btn">📤</button>
            </div>

            <div className="post-content">
              <span className="post-likes">
                {post.likes + (likedPosts[post.id] ? 1 : 0)} likes
              </span>
              <p>
                <strong>{post.user}</strong> {post.content}
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Home;
