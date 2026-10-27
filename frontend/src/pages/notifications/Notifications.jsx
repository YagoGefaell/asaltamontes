import "./Notifications.css";
import { useNavigate } from "react-router-dom";
import { useNotifications } from "../../features/notifications/hooks/useNotifications";
import {
  formatAge,
  notificationDestination,
  notificationText,
} from "../../features/notifications/utils/notificationText";
import Avatar from "../../shared/ui/Avatar";
import EmptyState from "../../shared/ui/EmptyState";

const Notifications = () => {
  const navigate = useNavigate();
  const { items, unreadCount, hasMore, loading, error, loadMore, refresh, markRead, markAllRead } = useNotifications();

  const open = (notification) => {
    markRead(notification);
    const destination = notificationDestination(notification);
    if (destination) navigate(destination.to, destination.state ? { state: destination.state } : undefined);
  };

  return (
    <div className="notifications-page">
      <div className="notifications-header">
        <h2>Notificaciones</h2>
        {unreadCount > 0 && (
          <button type="button" className="notif-mark-all" onClick={markAllRead}>
            Marcar todas como leídas
          </button>
        )}
      </div>

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
          const unread = !notification.readAt;
          const actor = notification.actor;
          return (
            <li key={notification.id}>
              <button
                type="button"
                className={`notification-item${unread ? " unread" : ""}`}
                onClick={() => open(notification)}
              >
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
                {unread && <span className="notif-dot" aria-label="Sin leer" />}
              </button>
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
