const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/auth";

// ---------------- LOGIN ----------------
export async function loginRequest({ email, password }) {
  const res = await fetch(`${API_URL}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include", // muy importante: enviar cookies
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) throw new Error("Login failed");
  return; // ya no hay accessToken/refreshToken en body
}

// ---------------- REGISTER ----------------
export async function registerRequest({ name, email, password }) {
  const res = await fetch(`${API_URL}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ name, email, password }),
  });

  if (!res.ok) throw new Error("Register failed");
  return; // tokens ya están en cookies
}

// ---------------- LOGOUT ----------------
export async function logoutRequest() {
  const res = await fetch(`${API_URL}/logout`, {
    method: "POST",
    credentials: "include", // enviar cookies para que se borren
  });

  if (!res.ok) throw new Error("Logout failed");
  return;
}
