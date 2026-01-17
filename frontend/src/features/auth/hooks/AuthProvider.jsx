import { useState, useEffect } from "react";
import { AuthContext } from "./auth.context";
import {
  registerRequest,
  loginRequest,
  logoutRequest,
  verifySessionRequest,
  refreshTokenRequest,
} from "../services/auth.service.js";

function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [checkingAuth, setCheckingAuth] = useState(true);

  // ---------------- Verificar sesión al cargar ----------------
  useEffect(() => {
    let mounted = true;

    const verifySession = async () => {
      try {
        const isValid = await verifySessionRequest();
        if (mounted) setIsAuthenticated(isValid);
      } catch {
        if (mounted) setIsAuthenticated(false);
      } finally {
        if (mounted) setCheckingAuth(false);
      }
    };

    verifySession();

    return () => {
      mounted = false;
    };
  }, []);

  // ---------------- LOGIN ----------------
  const login = async (username, password) => {
    try {
      await loginRequest({ username, password });
      setIsAuthenticated(true);
    } catch (err) {
      setIsAuthenticated(false);
      throw err; // Para que el componente que llama pueda mostrar mensaje de error
    }
  };

  // ---------------- REGISTER ----------------
  const register = async (fullName, username, email, password, confirmPassword) => {
    try {
      await registerRequest({ fullName, username, email, password, confirmPassword });
      setIsAuthenticated(true);
    } catch (err) {
      setIsAuthenticated(false);
      throw err;
    }
  };

  // ---------------- LOGOUT ----------------
  const logout = async () => {
    try {
      await logoutRequest();
    } finally {
      setIsAuthenticated(false);
    }
  };

  // ---------------- REFRESH TOKEN ----------------
  const refreshToken = async () => {
    try {
      await refreshTokenRequest();
      setIsAuthenticated(true);
      return true;
    } catch {
      setIsAuthenticated(false);
      return false;
    }
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
        refreshToken,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export default AuthProvider;
