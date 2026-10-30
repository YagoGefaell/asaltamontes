import { useLocation, useNavigate } from "react-router-dom";
import VerifyEmailPanel from "../../features/auth/components/VerifyEmailPanel.jsx";
import Button from "../../shared/forms/Button";
import "./VerifyEmail.css";

/** Where she lands when an action needs a verified email (403 EMAIL_NOT_VERIFIED, T24a2). Authenticated. */
export default function VerifyEmailRequired() {
  const navigate = useNavigate();
  const { state } = useLocation();

  return (
    <div className="verify-email">
      <h1 className="verify-email__title">Verifica tu email</h1>
      <p className="verify-email__text">Para hacer esto necesitas verificar tu email.</p>
      <VerifyEmailPanel />
      <Button variant="ghost" onClick={() => navigate(state?.from || "/home", { replace: true })}>
        Volver
      </Button>
    </div>
  );
}
