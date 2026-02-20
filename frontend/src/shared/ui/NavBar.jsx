import React from "react";
import { NavLink } from "react-router-dom";
import { FaHome, FaSearch, FaEnvelope, FaBell, FaUsers, FaUser } from "react-icons/fa";
import "./Navbar.css";

const Navbar = () => {
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
      <NavLink to="/notifications" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaBell />
      </NavLink>
      <NavLink to="/messages" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaEnvelope />
      </NavLink>
      <NavLink to="/me" className={({ isActive }) => (isActive ? "active" : "")}>
        <FaUser />
      </NavLink>
    </nav>
  );
};

export default Navbar;
