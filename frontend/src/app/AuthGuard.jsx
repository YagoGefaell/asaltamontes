import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../features/auth/hooks/useAuth.js";

function AuthGuard() {
  const { isAuthenticated, checkingAuth } = useAuth();

  if (checkingAuth) {
    return null;
  }

  return isAuthenticated ? <Outlet /> : <Navigate to="/access" replace />;
}

export default AuthGuard;
