import { useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { apiClient } from "../lib/api-client.js";

export const VERIFY_REQUIRED_PATH = "/verify-email/required";

/** Sends her to «Verifica tu email» whenever the API refuses an action with 403 EMAIL_NOT_VERIFIED (T24a2). */
export default function EmailVerificationRedirect() {
  const navigate = useNavigate();
  const location = useLocation();
  const pathname = useRef(location.pathname);

  useEffect(() => {
    pathname.current = location.pathname;
  }, [location.pathname]);

  useEffect(() => {
    apiClient.setEmailNotVerifiedCallback(() => {
      if (pathname.current.startsWith("/verify-email")) return;
      navigate(VERIFY_REQUIRED_PATH, { state: { from: pathname.current } });
    });
    return () => apiClient.setEmailNotVerifiedCallback(null);
  }, [navigate]);

  return null;
}
