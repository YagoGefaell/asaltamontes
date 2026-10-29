import "./Notifications.css";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useNotifications } from "../../features/notifications/hooks/useNotifications";
import {
  formatAge,
  notificationDestination,
  notificationText,
} from "../../features/notifications/utils/notificationText";
import FollowUserButton from "../../shared/components/FollowUserButton";
import Avatar from "../../shared/ui/Avatar";
import EmptyState from "../../shared/ui/EmptyState";
import PageHeader from "../../shared/ui/PageHeader";

const Notifications = () => {
  const navigate = useNavigate();
  const { items, hasMore, loading, error, loadMore, refresh, markRead, markAllRead } = useNotifications();

  // Everything is marked as read on entering (the Home counter clears), but what was new stays highlighted for the visit
  const [fresh, setFresh] = useState(() => new Set());
  const unseen = items.filter((n) => !n.readAt && !fresh.has(n.id)).map((n) => n.id);
  if (unseen.length > 0) setFresh(new Set([...fresh, ...unseen]));
  const hasUnread = items.some((n) => !n.readAt);
  useEffect(() => {
    if (hasUnread) markAllRead();
  }, [hasUnread, markAllRead]);

  const open = (notification) => {
    markRead(notification);
    const destination = notificationDestination(notification);
    if (destination) navigate(destination.to, destination.state ? { state: destination.state } : undefined);
  };

  return (
    <div className="notifications-page">
      <PageHeader
        title="Notificaciones"
        onBack={() => navigate(-1)}
      />

      {error && (
        <div className="notif-error" role="alert">
          <p>{error}</p>
          <button type="button" onClick={refresh}>Reintentar</button>
        </div>
      )}

      {!error && !loading && items.length === 0 && (
        <EmptyState title="No tienes notificaciones" description="Aquí verás quién te sigue y las novedades de tus quedadas y grupos." />
      )}

      <ul className="notifications-list">
        {items.map((notification) => {
          const { text, detail } = notificationText(notification);
          const unread = fresh.has(notification.id) || !notification.readAt;
          const actor = notification.actor;
          return (
            <li key={notification.id} className={`notification-item${unread ? " unread" : ""}`}>
              <button type="button" className="notif-main" onClick={() => open(notification)}>
                {actor ? (
                  <Avatar src={actor.profilePictureUrl} name={actor.fullName || actor.username} size="sm" />
                ) : (
                  <span className="notif-icon" aria-hidden="true">🔔</span>
                )}
                <span className="notif-content">
                  <span className="notif-text">{text}</span>
                  {detail && <span className="notif-detail">{detail}</span>}
                  <span className="notif-time">{formatAge(notification.createdAt)}</span>
                </span>
                {unread && <span className="notif-dot" aria-label="Nueva" />}
              </button>
              {notification.type === "NEW_FOLLOWER" && actor && (
                <div className="notif-action">
                  <FollowUserButton
                    userId={actor.username}
                    initialIsFollowing={Boolean(actor.followedByMe)}
                    followLabel="Seguir también"
                    unfollowLabel="Siguiendo"
                  />
                </div>
              )}
            </li>
          );
        })}
      </ul>

      {hasMore && (
        <div className="notif-more">
          <button type="button" disabled={loading} onClick={loadMore}>
            {loading ? "Cargando…" : "Cargar más"}
          </button>
        </div>
      )}
    </div>
  );
};

export default Notifications;
