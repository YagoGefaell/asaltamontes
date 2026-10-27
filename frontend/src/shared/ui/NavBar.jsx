import { NavLink } from "react-router-dom";
import { FaHome, FaSearch, FaUsers, FaUser, FaCalendarAlt } from "react-icons/fa";
import "./NavBar.css";

const TABS = [
  { to: "/home", label: "Inicio", icon: <FaHome aria-hidden="true" /> },
  { to: "/search", label: "Buscar", icon: <FaSearch aria-hidden="true" /> },
  { to: "/events", label: "Quedadas", icon: <FaCalendarAlt aria-hidden="true" /> },
  { to: "/communities", label: "Comunidades", icon: <FaUsers aria-hidden="true" /> },
  { to: "/me", label: "Perfil", icon: <FaUser aria-hidden="true" /> },
];

const Navbar = () => (
  <nav className="bottom-nav" aria-label="Navegación principal">
    {TABS.map(({ to, label, icon }) => (
      <NavLink key={to} to={to} aria-label={label} className={({ isActive }) => (isActive ? "active" : "")}>
        {icon}
      </NavLink>
    ))}
  </nav>
);

export default Navbar;
