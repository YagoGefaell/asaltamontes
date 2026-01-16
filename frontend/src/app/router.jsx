import { Routes, Route, Navigate } from "react-router-dom";
import AuthGuard from "./AuthGuard.jsx";

import MainLayout from "../shared/layouts/MainLayout.jsx";

import Home from "../pages/Home.jsx";
import Login from "../pages/Login.jsx";
import Register from "../pages/Register.jsx";
import Access from "../pages/Access.jsx";

import LogOut from "../pages/LogOut.jsx";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/access" element={<Access />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      <Route element={<MainLayout />}>
        <Route path="/" element={<AuthGuard><Home /></AuthGuard>} />
        <Route path="/home" element={<AuthGuard><Home /></AuthGuard>} />
        <Route path="/logout" element={<AuthGuard><LogOut /></AuthGuard>} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
