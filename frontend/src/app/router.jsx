import { Routes, Route, Navigate } from "react-router-dom";
import AuthGuard from "./AuthGuard.jsx";

import MainLayout from "../shared/layouts/MainLayout.jsx";

import Home from "../pages/Home.jsx";
import Login from "../pages/Login.jsx";
import Register from "../pages/Register.jsx";
import Access from "../pages/Access.jsx";
import Profile from "../pages/Profile.jsx";
import Search from "../pages/Search.jsx";
import EditProfile from "../pages/EditProfile.jsx";
import SettingsMenu from "../pages/settings/SettingsMenu.jsx";
import SettingsPage from "../shared/wrappers/SettingsPage.jsx";
import LogOut from "../pages/LogOut.jsx";

export function AppRouter() {
  return (
    <Routes>
      {/* Rutas públicas */}
      <Route path="/access" element={<Access />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      {/* Rutas protegidas */}
      <Route
        path="/"
        element={
          <AuthGuard>
            <MainLayout />
          </AuthGuard>
        }
      >
        <Route index element={<Home />} />
        <Route path="home" element={<Home />} />
        <Route path="logout" element={<LogOut />} />
        <Route path="me" element={<Profile />} />
        <Route path="search" element={<Search />} />
      </Route>

      {/* SETTINGS fuera de MainLayout pero protegido */}
      <Route
        path="/settings"
        element={
          <AuthGuard>
            <SettingsPage title="Ajustes" backTo="/me">
              <SettingsMenu />
            </SettingsPage>
          </AuthGuard>
        }
      />
      <Route
        path="/settings/profile"
        element={
          <AuthGuard>
            <SettingsPage title="Ajustes" backTo="/me">
              <EditProfile />
            </SettingsPage>
          </AuthGuard>
        }
      />

      {/* Catch-all */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
