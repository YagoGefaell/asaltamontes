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
import CommunityCreate from "../pages/communities/CommunityCreate.jsx";
import CommunityProfileDetail from "../pages/communities/CommunityProfileDetail.jsx";
import CommunitySettingsMenu from "../pages/communities/CommunitySettingsMenu.jsx";
import Events from "../pages/events/Events.jsx";
import EventDetail from "../pages/events/EventDetail.jsx";
import EventEditor from "../pages/events/EventEditor.jsx";
import EditProfile from "../pages/settings/EditProfile.jsx";
import PrivacySettings from "../pages/settings/PrivacySettings.jsx";
import NotificationSettings from "../pages/settings/NotificationSettings.jsx";
import SettingsMenu from "../pages/settings/SettingsMenu.jsx";
import SettingsPage from "../shared/layouts/SettingsPage.jsx";
import CommunitySettingsSection from "../pages/communities/settings/CommunitySettingsSection.jsx";
import CommunitySettingsPage from "../shared/layouts/CommunitySettingsPage.jsx";

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
          <Route path="communities/new" element={<CommunityCreate />} />
          <Route path="communities/:slug" element={<CommunityProfileDetail />} />
          <Route path="events" element={<Events />} />
          <Route path="events/new" element={<EventEditor />} />
          <Route path="events/:id" element={<EventDetail />} />
          <Route path="events/:id/edit" element={<EventEditor />} />
        </Route>

        {/* ---- Rutas de Followers/Following (sin navbar) ---- */}
        <Route path="/me/followers" element={<Followers />} />
        <Route path="/me/following" element={<Following />} />

        <Route path="/users/:username/followers" element={<Followers />} />
        <Route path="/users/:username/following" element={<Following />} />

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

        <Route
          path="/settings/privacy"
          element={
            <SettingsPage title="Privacidad" backTo="/settings">
              <PrivacySettings />
            </SettingsPage>
          }
        />

        <Route
          path="/settings/notifications"
          element={
            <SettingsPage title="Notificaciones" backTo="/settings">
              <NotificationSettings />
            </SettingsPage>
          }
        />

        <Route
          path="/communities/:slug/settings"
          element={
            <CommunitySettingsPage>
              <CommunitySettingsMenu />
            </CommunitySettingsPage>
          }
        />

        <Route
          path="/communities/:slug/settings/:section"
          element={
            <CommunitySettingsPage>
              <CommunitySettingsSection />
            </CommunitySettingsPage>
          }
        />
      </Route>

      {/* -------------------- CATCH ALL -------------------- */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
