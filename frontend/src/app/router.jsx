import { Routes, Route, Navigate } from "react-router-dom";
import AuthGuard from "./guards/AuthGuard.jsx";

import MainLayout from "../shared/layouts/MainLayout.jsx";

import Home from "../pages/home/Home.jsx";
import Login from "../pages/access/Login.jsx";
import Register from "../pages/access/Register.jsx";
import Access from "../pages/access/Access.jsx";
import Profile from "../pages/profile/Profile.jsx";
import Search from "../pages/search/Search.jsx";
import Messages from "../pages/messages/Messages.jsx";
import Notifications from "../pages/notifications/Notifications.jsx";
import Communities from "../pages/communities/Communities.jsx";
import EditProfile from "../pages/settings/EditProfile.jsx";
import SettingsMenu from "../pages/settings/SettingsMenu.jsx";
import SettingsPage from "../shared/layouts/SettingsPage.jsx";
import LogOut from "../pages/access/LogOut.jsx";

export function AppRouter() {
  return (
    <Routes>
      {/* -------------------- PÚBLICAS -------------------- */}
      <Route path="/access" element={<Access />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />

      {/* -------------------- PRIVADAS -------------------- */}
      <Route
        element={
          <AuthGuard>
            <></>
          </AuthGuard>
        }
      >
        {/* ---- Rutas con MainLayout ---- */}
        <Route path="/" element={<MainLayout />}>
          <Route index element={<Home />} />
          <Route path="home" element={<Home />} />
          <Route path="logout" element={<LogOut />} />
          <Route path="me" element={<Profile />} />
          <Route path="search" element={<Search />} />
          <Route path="messages" element={<Messages />} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="communities" element={<Communities />} />
        </Route>

        {/* ---- Rutas de Settings ---- */}
        <Route
          path="/settings"
          element={
            <SettingsPage title="Ajustes" backTo="/me">
              <SettingsMenu />
            </SettingsPage>
          }
        />

        <Route
          path="/settings/profile"
          element={
            <SettingsPage title="Ajustes" backTo="/settings">
              <EditProfile />
            </SettingsPage>
          }
        />
      </Route>

      {/* -------------------- CATCH ALL -------------------- */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
