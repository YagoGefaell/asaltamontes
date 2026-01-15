import { useState, useEffect } from "react";
import { AuthContext } from "./auth.context";
import { registerRequest, loginRequest, logoutRequest, verifySessionRequest } from "../services/auth.service.js";

function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [checkingAuth, setCheckingAuth] = useState(true);

  // ---------------- Verificar sesión al cargar ----------------
  useEffect(() => {
    const verifySession = async () => {
      try {
        await verifySessionRequest();
        setIsAuthenticated(true);
      } catch (error) {
        setIsAuthenticated(false);
      } finally {
        setCheckingAuth(false);
      }
    };

    verifySession();
  }, []);

  // ---------------- LOGIN ----------------
  const login = async (username, password) => {
    await loginRequest({ username, password });
    setIsAuthenticated(true);
  };

  // ---------------- REGISTER ----------------
  const register = async (username, email, password) => {
    await registerRequest({ username, email, password });
    setIsAuthenticated(true);
  };

  // ---------------- LOGOUT ----------------
  const logout = async () => {
    await logoutRequest();
    setIsAuthenticated(false);
  };

  // ---------------- CONTEXT PROVIDER ----------------
  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        checkingAuth,
        login,
        logout,
        register,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export default AuthProvider;