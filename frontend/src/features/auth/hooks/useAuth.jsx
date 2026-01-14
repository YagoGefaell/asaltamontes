import { createContext, useContext, useState, useEffect } from "react";
import { loginRequest, registerRequest } from "../services/auth.service.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(null);
  const [refreshToken, setRefreshToken] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [checkingAuth, setCheckingAuth] = useState(true);

  useEffect(() => {
    const savedToken = localStorage.getItem("token");
    const savedRefreshToken = localStorage.getItem("refreshToken");

    if (!savedToken) {
      setCheckingAuth(false);
      return;
    }

    fetch("http://localhost:8080/auth/verify", {
      headers: { Authorization: `Bearer ${savedToken}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error("Token inválido");
        setToken(savedToken);
        setRefreshToken(savedRefreshToken);
        setIsAuthenticated(true);
      })
      .catch(() => {
        setToken(null);
        setRefreshToken(null);
        localStorage.removeItem("token");
        localStorage.removeItem("refreshToken");
      })
      .finally(() => {
        setCheckingAuth(false);
      });
  }, []);

  const login = async (email, password) => {
    const { accessToken, refreshToken } = await loginRequest({ email, password });
    console.log("Logging in with tokens:", accessToken, refreshToken);
    setUser({ email }); // or fetch user info if available
    setToken(accessToken);
    setRefreshToken(refreshToken);
    localStorage.setItem("token", accessToken);
    localStorage.setItem("refreshToken", refreshToken);
  };

  const logout = () => {
    setToken(null);
    setRefreshToken(null);
    localStorage.removeItem("token");
    localStorage.removeItem("refreshToken");
  };

  return (
    <AuthContext.Provider
      value={{ token, refreshToken, isAuthenticated, checkingAuth, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
