import { BrowserRouter } from "react-router-dom";
import AuthProvider from "../features/auth/hooks/AuthProvider.jsx";
import { GoogleOAuthProvider } from '@react-oauth/google';
import { UserProfileProvider } from "../features/users/hooks/UserProfileProvider.jsx";
import ScrollToTop from "../shared/components/ScrollToTop.jsx";
import { NotificationsProvider } from "../features/notifications/components/notifications.provider.jsx";
import { ConversationsProvider } from "../features/messages/components/conversations.provider.jsx";

export function Providers({ children }) {
  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  return (
    <GoogleOAuthProvider clientId={googleClientId}>
      <BrowserRouter>
        <ScrollToTop />
        <AuthProvider>
          <UserProfileProvider>
            <ConversationsProvider>
              <NotificationsProvider>
                {children}
              </NotificationsProvider>
            </ConversationsProvider>
          </UserProfileProvider>
        </AuthProvider>
      </BrowserRouter>
    </GoogleOAuthProvider>
  );
}
