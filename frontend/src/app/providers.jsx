import { BrowserRouter } from "react-router-dom";
import AuthProvider from "../features/auth/hooks/AuthProvider.jsx";
import { UserProfileProvider } from "../features/users/hooks/UserProfileProvider.jsx";
import ScrollToTop from "../shared/components/ScrollToTop.jsx";
import { ConversationsProvider } from "../features/messages/components/conversations.provider.jsx";

export function Providers({ children }) {
  return (
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
  );
}
