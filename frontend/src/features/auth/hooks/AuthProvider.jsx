import { useState, useEffect } from "react";
import { AuthContext } from "./auth.context";

function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [checkingAuth, setCheckingAuth] = useState(true);

  // ---------------- Verificar sesión al cargar ----------------
  useEffect(() => {
    const verifySession = async () => {
      try {
        const res = await fetch("http://localhost:8080/auth/verify", {
          method: "GET",
          credentials: "include",
        });

        setIsAuthenticated(res.ok);
      } catch (error) {
        console.error("Error verificando sesión:", error);
        setIsAuthenticated(false);
      } finally {
        setCheckingAuth(false);
      }
    };

    verifySession();
  }, []);

  // ---------------- LOGIN ----------------
  const login = async (email, password) => {
    const res = await fetch("http://localhost:8080/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include", // enviar cookies
      body: JSON.stringify({ email, password }),
    });

    if (!res.ok) throw new Error("Usuario o contraseña incorrectos");
    setIsAuthenticated(true);
  };

  // ---------------- REGISTER ----------------
  const register = async (name, email, password) => {
    const res = await fetch("http://localhost:8080/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ name, email, password }),
    });

    if (!res.ok) throw new Error("Patata");
    setIsAuthenticated(true);
  };

  // ---------------- LOGOUT ----------------
  const logout = async () => {
    await fetch("http://localhost:8080/auth/logout", {
      method: "POST",
      credentials: "include", // enviar cookies para borrarlas en backend
    });
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