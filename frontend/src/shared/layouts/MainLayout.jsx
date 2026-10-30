// Layout.jsx
import { Outlet } from "react-router-dom";
import VerifyEmailBanner from "../../features/auth/components/VerifyEmailBanner";
import Navbar from "../ui/NavBar";

export default function MainLayout() {
  return (
    <>
      <VerifyEmailBanner />
      <main>
        <Outlet />
      </main>
      <Navbar />
    </>
  );
}
