import { Navigate } from "react-router-dom";
import { useAuth } from "../features/auth/hooks/useAuth.js";

function AuthGuard({ children }) {
  const { isAuthenticated, checkingAuth } = useAuth();

  if (checkingAuth) {
    return null;
  }

  return isAuthenticated ? children : <Navigate to="/access" replace />;
}

export default AuthGuard;