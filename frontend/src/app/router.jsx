import { Routes, Route, Navigate } from "react-router-dom";
import AuthGuard from "./guards/AuthGuard.jsx";
import PublicOnlyGuard from "./guards/PublicOnlyGuard.jsx";

import MainLayout from "../shared/layouts/MainLayout.jsx";

import Home from "../pages/home/Home.jsx";
import Login from "../pages/access/Login.jsx";
import Register from "../pages/access/Register.jsx";
import Access from "../pages/access/Access.jsx";
import Profile from "../pages/profile/Profile.jsx";
import UserProfile from "../pages/profile/UserProfile.jsx";
import Followers from "../pages/profile/Followers.jsx";
import Following from "../pages/profile/Following.jsx";
import Search from "../pages/search/Search.jsx";
import Messages from "../pages/messages/Messages.jsx";
import Notifications from "../pages/notifications/Notifications.jsx";
import Communities from "../pages/communities/Communities.jsx";
import EditProfile from "../pages/settings/EditProfile.jsx";
import SettingsMenu from "../pages/settings/SettingsMenu.jsx";
import SettingsPage from "../shared/layouts/SettingsPage.jsx";

export function AppRouter() {
  return (
    <Routes>
      {/* -------------------- PÚBLICAS -------------------- */}
      <Route element={<PublicOnlyGuard />}>
        <Route path="/access" element={<Access />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
      </Route>

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
          <Route path="me" element={<Profile />} />
          <Route path="users/:username" element={<UserProfile />} />
          <Route path="search" element={<Search />} />
          <Route path="messages" element={<Messages />} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="communities" element={<Communities />} />
        </Route>

        {/* ---- Rutas de Followers/Following (sin navbar) ---- */}
        <Route path="/me/followers" element={<Followers />} />
        <Route path="/me/following" element={<Following />} />

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
