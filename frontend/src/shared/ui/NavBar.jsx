import React from "react";
import { NavLink } from "react-router-dom";
import { FaHome, FaSearch, FaEnvelope, FaBell, FaUsers, FaUser, FaCalendarAlt } from "react-icons/fa";
import { ConversationsContext } from "../../features/messages/hooks/conversations.context";
import { useNotifications } from "../../features/notifications/hooks/useNotifications";
import "./NavBar.css";

const Navbar = () => {
  const { totalUnreadCount } = React.useContext(ConversationsContext);
  const { unreadCount: unreadNotifications } = useNotifications();

  return (
    <nav className="bottom-nav">
      <NavLink to="/home" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaHome />
      </NavLink>
      <NavLink to="/search" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaSearch />
      </NavLink>
      <NavLink to="/communities" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaUsers />
      </NavLink>
      <NavLink to="/events" aria-label="Quedadas" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaCalendarAlt />
      </NavLink>
      <NavLink
        to="/notifications"
        aria-label={unreadNotifications > 0 ? `Notificaciones, ${unreadNotifications} sin leer` : "Notificaciones"}
        className={({ isActive }) => (isActive ? "active" : "")}
      >
        <FaBell />
        {unreadNotifications > 0 && (
          <span className="unread-count" aria-hidden="true">{unreadNotifications > 9 ? "9+" : unreadNotifications}</span>
        )}
      </NavLink>
      <NavLink to="/messages" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaEnvelope />
        {totalUnreadCount > 0 && (
          <span className="unread-count">{totalUnreadCount > 9 ? "9+" : totalUnreadCount}</span>
        )}
      </NavLink>
      <NavLink to="/me" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaUser />
      </NavLink>
    </nav>
  );
};

export default Navbar;
