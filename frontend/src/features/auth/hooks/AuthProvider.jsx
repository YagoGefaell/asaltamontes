import { useState, useEffect } from "react";
import { AuthContext } from "./auth.context";
import { apiClient } from "../../../lib/api-client.js";
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
  const [currentUser, setCurrentUser] = useState(null);

  // ---------------- Verificar sesión al cargar ----------------
  useEffect(() => {
    let mounted = true;

    apiClient.setLogoutCallback(() => {
      if (mounted) {
        setIsAuthenticated(false)
      }
    })

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

  // ---------------- UPDATE FOLLOW COUNTS ----------------
  const incrementFollowing = () => {
    setCurrentUser(prev => prev ? {
      ...prev,
      followingCount: (prev.followingCount || 0) + 1
    } : null);
  };

  const decrementFollowing = () => {
    setCurrentUser(prev => prev ? {
      ...prev,
      followingCount: Math.max(0, (prev.followingCount || 0) - 1)
    } : null);
  };

  const incrementFollowers = () => {
    setCurrentUser(prev => prev ? {
      ...prev,
      followersCount: (prev.followersCount || 0) + 1
    } : null);
  };

  const decrementFollowers = () => {
    setCurrentUser(prev => prev ? {
      ...prev,
      followersCount: Math.max(0, (prev.followersCount || 0) - 1)
    } : null);
  };

  // ---------------- CONTEXT PROVIDER ----------------
  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        checkingAuth,
        currentUser,
        login,
        logout,
        register,
        refreshToken,
        setCurrentUser,
        incrementFollowing,
        decrementFollowing,
        incrementFollowers,
        decrementFollowers,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export default AuthProvider;
