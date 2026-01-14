import { Routes, Route, Navigate } from "react-router-dom";
import AuthGuard from "./AuthGuard.jsx";

import Home from "../pages/Home.jsx";
import Login from "../pages/Login.jsx";
import Register from "../pages/Register.jsx";
import Access from "../pages/Access.jsx";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/access" element={<Access />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      <Route path="/" element={<AuthGuard><Home /></AuthGuard>} />
      <Route path="/home" element={<AuthGuard><Home /></AuthGuard>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
