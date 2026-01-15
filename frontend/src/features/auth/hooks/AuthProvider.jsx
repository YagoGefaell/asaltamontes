import { useState, useEffect } from "react";
import { AuthContext } from "./auth.context.js";
import { loginRequest, registerRequest } from "../services/auth.service";

function AuthProvider({ children }) {
  const tokenFromStorage = localStorage.getItem("token");
  const refreshFromStorage = localStorage.getItem("refreshToken");

  const [token, setToken] = useState(tokenFromStorage);
  const [refreshToken, setRefreshToken] = useState(refreshFromStorage);
  const [isAuthenticated, setIsAuthenticated] = useState(!!tokenFromStorage);
  const [checkingAuth, setCheckingAuth] = useState(!!tokenFromStorage);

  useEffect(() => {
    if (!token) {
      if (!refreshToken) return
      fetch("http://localhost:8080/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken })
      })
      .then((res) => {
          if (!res.ok) throw new Error();
          return res.json();
        })
      .then((data => {
          setToken(data.accessToken);
          localStorage.setItem("token", data.accessToken);
          setIsAuthenticated(true);
        }))
      .catch(() => {
          localStorage.clear();
          setToken(null);
          setRefreshToken(null);
          setIsAuthenticated(false);
        });
    }

    fetch("http://localhost:8080/auth/verify", {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error();
        setIsAuthenticated(true);
      })
      .catch(() => {
        localStorage.clear();
        setToken(null);
        setRefreshToken(null);
        setIsAuthenticated(false);
      })
      .finally(() => setCheckingAuth(false));
  }, [token]);

  const login = async (email, password) => {
    const { accessToken, refreshToken } = await loginRequest({
      email,
      password,
    });
    setToken(accessToken);
    setRefreshToken(refreshToken);
    setIsAuthenticated(true);
    localStorage.setItem("token", accessToken);
    localStorage.setItem("refreshToken", refreshToken);
  };

  const logout = () => {
    localStorage.clear();
    setToken(null);
    setRefreshToken(null);
    setIsAuthenticated(false);
  };

  const register = async (name, email, password) => {
    const { accessToken, refreshToken } = await registerRequest({
      name,
      email,
      password,
    });
    setToken(accessToken);
    setRefreshToken(refreshToken);
    setIsAuthenticated(true);
    localStorage.setItem("token", accessToken);
    localStorage.setItem("refreshToken", refreshToken);
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        refreshToken,
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