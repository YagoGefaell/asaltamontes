import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "../features/auth/hooks/useAuth.jsx"; // asegúrate de la ruta

export function Providers({ children }) {
  return (
    <BrowserRouter>
      <AuthProvider>
        {children}
      </AuthProvider>
    </BrowserRouter>
  );
}
