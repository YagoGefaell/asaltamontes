import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth.js";

function PublicOnlyGuard() {
  const { isAuthenticated, checkingAuth } = useAuth();

  if (checkingAuth) {
    return null;
  }

  return isAuthenticated ? <Navigate to="/home" replace /> : <Outlet />;
}

export default PublicOnlyGuard;
