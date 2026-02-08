import Button from "../../shared/forms/Button.jsx";
import { useAuth } from "../../features/auth/hooks/useAuth.js";

function LogOut() {
    const { logout } = useAuth();
    return <Button variant="primary" onClick={logout}>Log Out</Button>;
}

export default LogOut;