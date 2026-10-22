import { BrowserRouter } from "react-router-dom";
import AuthProvider from "../features/auth/hooks/AuthProvider.jsx";
import { GoogleOAuthProvider } from '@react-oauth/google';
import { UserProfileProvider } from "../features/users/hooks/UserProfileProvider.jsx";
import ScrollToTop from "../shared/components/ScrollToTop.jsx";
import { ToastProvider } from "../shared/ui/Toast.jsx";
import { ConversationsProvider } from "../features/messages/components/conversations.provider.jsx";

export function Providers({ children }) {
  const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;

  return (
    <GoogleOAuthProvider clientId={googleClientId}>
      <BrowserRouter>
        <ScrollToTop />
        <ToastProvider>
        <AuthProvider>
          <UserProfileProvider>
            <ConversationsProvider>
              {children}
            </ConversationsProvider>
          </UserProfileProvider>
        </AuthProvider>
        </ToastProvider>
      </BrowserRouter>
    </GoogleOAuthProvider>
  );
}
