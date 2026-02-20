import "./Notifications.css";
import { useState } from "react";

const Notifications = () => {
  const [notifications] = useState([
    { id: 1, icon: "❤️", user: "María García", action: "le gustó tu publicación", time: "2h" },
    { id: 2, icon: "💬", user: "Laura Martínez", action: "comentó en tu post", time: "4h" },
    { id: 3, icon: "👥", user: "Sofía López", action: "empieza a seguirte", time: "1d" },
    { id: 4, icon: "❤️", user: "Ana Rodríguez", action: "le gustó tu publicación", time: "2d" },
  ]);

  return (
    <div className="notifications-page">
      <div className="notifications-header">
        <h2>Notificaciones</h2>
      </div>

      <div className="notifications-list">
        {notifications.map((notif) => (
          <div key={notif.id} className="notification-item">
            <span className="notif-icon">{notif.icon}</span>
            <div className="notif-content">
              <p className="notif-text">
                <strong>{notif.user}</strong> {notif.action}
              </p>
              <span className="notif-time">{notif.time}</span>
            </div>
            <button className="notif-action">→</button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Notifications;
