import { Navigate, Outlet } from "react-router-dom";
import { useIsStaff } from "../../features/admin/hooks/useIsStaff.js";

/**
 * Moderation panel (ADMIN and MODERATOR). Only a convenience: the backend answers 403 to anyone else,
 * so what this decides is whether to show the screens, not who is allowed.
 */
export function AdminGuard() {
  const access = useIsStaff();

  if (access === "checking") return null;
  return access === "staff" ? <Outlet /> : <Navigate to="/" replace />;
}
