import { BrowserRouter } from "react-router-dom";
import AuthProvider from "../features/auth/hooks/AuthProvider.jsx";
import { UserProfileProvider } from "../features/users/hooks/UserProfileProvider.jsx";

export function Providers({ children }) {
  return (
    <BrowserRouter>
      <AuthProvider>
        <UserProfileProvider>
          {children}
        </UserProfileProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
