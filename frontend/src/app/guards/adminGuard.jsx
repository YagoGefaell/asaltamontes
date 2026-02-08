import { Navigate } from "react-router-dom";

export function AdminGuard({ isAdmin, children }) {
  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }

  return children;
}
