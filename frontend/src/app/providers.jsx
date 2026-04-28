import { BrowserRouter } from "react-router-dom";
import AuthProvider from "../features/auth/hooks/AuthProvider.jsx";
import { GoogleOAuthProvider } from '@react-oauth/google';
import { UserProfileProvider } from "../features/users/hooks/UserProfileProvider.jsx";
import ScrollToTop from "../shared/components/ScrollToTop.jsx";
import { ConversationsProvider } from "../features/messages/components/conversations.provider.jsx";

export function Providers({ children }) {
  const googleClientId = "944293775531-hmr3cakfn29r8gi0q3rr8h2mgpgf0q1n.apps.googleusercontent.com";

  console.log(googleClientId);

  return (
    <GoogleOAuthProvider clientId={googleClientId}>
      <BrowserRouter>
        <ScrollToTop />
        <AuthProvider>
          <UserProfileProvider>
            <ConversationsProvider>
              {children}
            </ConversationsProvider>
          </UserProfileProvider>
        </AuthProvider>
      </BrowserRouter>
    </GoogleOAuthProvider>
  );
}
