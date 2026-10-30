// Layout.jsx
import { Outlet } from "react-router-dom";
import Navbar from "../ui/NavBar";

export default function MainLayout() {
  return (
    <>
      <main>
        <Outlet />
      </main>
      <Navbar />
    </>
  );
}
