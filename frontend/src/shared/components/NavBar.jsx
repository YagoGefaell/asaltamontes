import React from "react";
import { NavLink } from "react-router-dom";
import { FaHome, FaSearch, FaUser, FaEnvelope } from "react-icons/fa";
import "./Navbar.css";

const Navbar = () => {
  return (
    <nav className="bottom-nav">
      <NavLink to="/" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaHome />
      </NavLink>
      <NavLink to="/search" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaSearch />
      </NavLink>
      <NavLink to="/messages" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaEnvelope />
      </NavLink>
      <NavLink to="/profile" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaUser />
      </NavLink>
    </nav>
  );
};

export default Navbar;
